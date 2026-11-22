package com.bot.telegram.service;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class WorkoutService implements IWorkoutService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutService.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final WorkoutSessionRepository workoutRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId appZoneId;

    public WorkoutService(GeminiService geminiService, WorkoutSessionRepository workoutRepository, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this(geminiService, workoutRepository, objectMapper, meterRegistry, null, DEFAULT_ZONE);
    }

    public WorkoutService(GeminiService geminiService, WorkoutSessionRepository workoutRepository, ObjectMapper objectMapper, MeterRegistry meterRegistry, TransactionTemplate transactionTemplate) {
        this(geminiService, workoutRepository, objectMapper, meterRegistry, transactionTemplate, DEFAULT_ZONE);
    }

    @Autowired
    public WorkoutService(GeminiService geminiService, WorkoutSessionRepository workoutRepository, ObjectMapper objectMapper, MeterRegistry meterRegistry, TransactionTemplate transactionTemplate, ZoneId appZoneId) {
        this.geminiService = geminiService;
        this.workoutRepository = workoutRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = transactionTemplate;
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    private <T> T inTransaction(Supplier<T> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        return transactionTemplate.execute(status -> action.get());
    }

    public static class WorkoutUpdateResult {
        public final WorkoutSession session;
        public final int startIndex;
        public final List<WorkoutDto.ExerciseDto> addedExercises;
        public final boolean createdNewSession;

        public WorkoutUpdateResult(WorkoutSession session, int startIndex, List<WorkoutDto.ExerciseDto> addedExercises, boolean createdNewSession) {
            this.session = session;
            this.startIndex = startIndex;
            this.addedExercises = addedExercises;
            this.createdNewSession = createdNewSession;
        }
    }

    @Transactional
    public WorkoutSession createOrUpdateDraftWorkout(UserTelegram user, String title, Integer userMessageId) {
        Optional<WorkoutSession> optLatest = getLatestWorkoutForToday(user);
        WorkoutSession session;
        if (optLatest.isPresent()) {
            session = optLatest.get();
            session.setRawInput(title != null ? title : "Treino");
            if (userMessageId != null) {
                session.setUserMessageId(userMessageId);
            }
        } else {
            session = buildNewSession(title, null, user, userMessageId);
            meterRegistry.counter("shapelog.workouts.created").increment();
        }
        return workoutRepository.save(session);
    }

    @Override
    @Transactional
    public WorkoutSession createNewDraftWorkout(UserTelegram user, String title, Integer userMessageId) {
        WorkoutSession session = buildNewSession(title, null, user, userMessageId);
        meterRegistry.counter("shapelog.workouts.created").increment();
        return workoutRepository.save(session);
    }

    private WorkoutSession buildNewSession(String title, String description, UserTelegram user, Integer userMessageId) {
        return WorkoutSession.builder()
                .user(user)
                .rawInput(title != null && !title.isBlank() ? title.trim() : "Treino")
                .description(description)
                .exercisesJson("[]")
                .createdAt(Instant.now())
                .userMessageId(userMessageId)
                .build();
    }

    @Override
    public WorkoutUpdateResult addExercisesOrCreateWorkout(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId) {
        WorkoutDto dto = parseAndValidateWorkout(text, audioBytes);
        return inTransaction(() -> {
            Optional<WorkoutSession> optLatest = getLatestWorkoutForToday(user);
            boolean createdNewSession = optLatest.isEmpty();
            WorkoutSession session;
            if (createdNewSession) {
                String inferredTitle = (dto.getDescription() != null && !dto.getDescription().isBlank())
                        ? dto.getDescription().trim()
                        : "Treino";
                session = buildNewSession(inferredTitle, dto.getDescription(), user, userMessageId);
                meterRegistry.counter("shapelog.workouts.created").increment();
            } else {
                session = optLatest.get();
            }
            return appendParsedExercises(session, dto, userMessageId, createdNewSession);
        });
    }

    private WorkoutDto parseAndValidateWorkout(String text, byte[] audioBytes) {
        WorkoutDto dto = geminiService.parseWorkout(text, audioBytes);
        if (dto == null || dto.getExercises() == null || dto.getExercises().isEmpty()) {
            throw new IllegalArgumentException("Não foi possível identificar nenhum exercício na sua mensagem. Envie os exercícios com séries e repetições (ex: Supino 3x10 80kg)!");
        }
        return dto;
    }

    private WorkoutUpdateResult appendParsedExercises(WorkoutSession session, WorkoutDto dto, Integer userMessageId, boolean createdNewSession) {
        if (dto.getDurationMinutes() != null) {
            int existingDuration = session.getDurationMinutes() != null ? session.getDurationMinutes() : 0;
            session.setDurationMinutes(existingDuration + dto.getDurationMinutes());
        }
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        sanitizeExerciseIds(existingExercises);
        int startIndex = existingExercises.size();
        List<WorkoutDto.ExerciseDto> added = dto.getExercises();
        if (added != null) {
            // Sobrescreve qualquer "id" eventualmente preenchido pelo Gemini (ex: "1") para evitar colisão
            for (WorkoutDto.ExerciseDto ex : added) {
                if (ex != null) {
                    ex.setId(generateExerciseId());
                }
            }
            existingExercises.addAll(added);
        }
        sanitizeExerciseIds(existingExercises);
        session.setExercisesJson(serializeExercises(existingExercises));
        if (userMessageId != null) {
            session.setUserMessageId(userMessageId);
        }
        return new WorkoutUpdateResult(workoutRepository.save(session), startIndex, added, createdNewSession);
    }

    @Override
    public WorkoutSession editExerciseById(Long workoutId, String exerciseId, String text, byte[] audioBytes) {
        WorkoutSession preCheck = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        List<WorkoutDto.ExerciseDto> preExercises = deserializeExercises(preCheck.getExercisesJson());
        int preIdx = findExerciseIndex(preExercises, exerciseId);
        if (preIdx < 0) {
            throw new IllegalArgumentException("Este exercício não foi encontrado ou já foi removido.");
        }
        WorkoutDto.ExerciseDto originalEx = preExercises.get(preIdx);
        WorkoutDto dto = geminiService.parseExerciseUpdate(originalEx, text, audioBytes);
        if (dto == null || dto.getExercises() == null || dto.getExercises().isEmpty()) {
            throw new IllegalArgumentException("Não foi possível identificar o exercício atualizado. Tente descrever novamente com séries e peso!");
        }
        return inTransaction(() -> {
            WorkoutSession session = workoutRepository.findById(workoutId)
                    .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
            List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
            int idx = findExerciseIndex(existingExercises, exerciseId);
            if (idx < 0) {
                throw new IllegalArgumentException("Este exercício não foi encontrado ou já foi removido.");
            }
            WorkoutDto.ExerciseDto oldEx = existingExercises.get(idx);
            WorkoutDto.ExerciseDto newEx = dto.getExercises().get(0);
            newEx.setId(oldEx.getId() != null && !oldEx.getId().isBlank() ? oldEx.getId() : generateExerciseId());
            existingExercises.set(idx, newEx);
            sanitizeExerciseIds(existingExercises);
            session.setExercisesJson(serializeExercises(existingExercises));
            return workoutRepository.save(session);
        });
    }

    @Override
    @Transactional
    public WorkoutSession removeExerciseById(Long workoutId, String exerciseId) {
        WorkoutSession session = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        int idx = findExerciseIndex(existingExercises, exerciseId);
        if (idx < 0) {
            throw new IllegalArgumentException("Este exercício já foi removido.");
        }
        existingExercises.remove(idx);
        if (existingExercises.isEmpty()) {
            session.setExercisesJson("[]");
            workoutRepository.delete(session);
            return session;
        }
        sanitizeExerciseIds(existingExercises);
        session.setExercisesJson(serializeExercises(existingExercises));
        return workoutRepository.save(session);
    }

    @Override
    public int findExerciseIndex(List<WorkoutDto.ExerciseDto> exercises, String idOrIndex) {
        if (exercises == null || idOrIndex == null || idOrIndex.isBlank()) {
            return -1;
        }
        for (int i = 0; i < exercises.size(); i++) {
            if (idOrIndex.equals(exercises.get(i).getId())) {
                return i;
            }
        }
        try {
            int legacyIdx = Integer.parseInt(idOrIndex);
            if (legacyIdx >= 0 && legacyIdx < exercises.size() && exercises.get(legacyIdx).getId() == null) {
                return legacyIdx;
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private void sanitizeExerciseIds(List<WorkoutDto.ExerciseDto> exercises) {
        if (exercises == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (WorkoutDto.ExerciseDto ex : exercises) {
            if (ex == null) {
                continue;
            }
            String id = ex.getId();
            if (id == null || id.isBlank() || !seen.add(id)) {
                String fresh = generateUniqueId(seen);
                ex.setId(fresh);
                seen.add(fresh);
            }
        }
    }

    private String generateUniqueId(Set<String> seen) {
        String candidate = generateExerciseId();
        while (seen.contains(candidate)) {
            candidate = generateExerciseId();
        }
        return candidate;
    }

    private String generateExerciseId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    @Transactional
    public void deleteWorkout(Long workoutId) {
        workoutRepository.deleteById(workoutId);
    }

    @Override
    public Optional<WorkoutSession> findByIdAndUserId(Long workoutId, Long userId) {
        return workoutRepository.findByIdAndUserId(workoutId, userId);
    }

    public boolean existeWorkout(Long workoutId) {
        return workoutRepository.existsById(workoutId);
    }

    public List<WorkoutSession> getWorkoutsForDate(UserTelegram user, LocalDate date) {
        Instant start = date.atStartOfDay(appZoneId).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(appZoneId).toInstant();
        return workoutRepository.findByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(user, start, end);
    }

    public Optional<WorkoutSession> getLatestWorkoutForToday(UserTelegram user) {
        LocalDate today = LocalDate.now(appZoneId);
        Instant start = today.atStartOfDay(appZoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(appZoneId).toInstant();
        return workoutRepository.findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(user, start, end);
    }

    private String serializeExercises(List<WorkoutDto.ExerciseDto> exercises) {
        try {
            return objectMapper.writeValueAsString(exercises);
        } catch (Exception e) {
            log.error("Falha ao serializar exercícios do treino", e);
            throw new IllegalStateException("Falha ao salvar os exercícios do treino.", e);
        }
    }

    public List<WorkoutDto.ExerciseDto> deserializeExercises(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return new java.util.ArrayList<>();
            }
            WorkoutDto.ExerciseDto[] array = objectMapper.readValue(json, WorkoutDto.ExerciseDto[].class);
            return new java.util.ArrayList<>(java.util.Arrays.asList(array));
        } catch (Exception e) {
            log.error("Falha ao desserializar exercisesJson: {}", json, e);
            throw new IllegalStateException("Falha ao ler os exercícios salvos do treino.", e);
        }
    }
}
