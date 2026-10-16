package com.bot.telegram.service;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkoutService implements IWorkoutService {

    private final WorkoutSessionRepository workoutRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public WorkoutService(GeminiService geminiService, WorkoutSessionRepository workoutRepository, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.geminiService = geminiService;
        this.workoutRepository = workoutRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public static class WorkoutUpdateResult {
        public final WorkoutSession session;
        public final int startIndex;
        public final List<WorkoutDto.ExerciseDto> addedExercises;
        public WorkoutUpdateResult(WorkoutSession session, int startIndex, List<WorkoutDto.ExerciseDto> addedExercises) {
            this.session = session;
            this.startIndex = startIndex;
            this.addedExercises = addedExercises;
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
            session = WorkoutSession.builder()
                    .user(user)
                    .rawInput(title != null ? title : "Treino")
                    .exercisesJson("[]")
                    .createdAt(LocalDateTime.now())
                    .userMessageId(userMessageId)
                    .build();
            meterRegistry.counter("shapelog.workouts.created").increment();
        }
        return workoutRepository.save(session);
    }

    public WorkoutUpdateResult addExercisesToDraft(Long workoutId, String text, byte[] audioBytes, Integer userMessageId) {
        WorkoutSession session = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        WorkoutDto dto = geminiService.parseWorkout(text, audioBytes);
        if (dto == null || dto.getExercises() == null || dto.getExercises().isEmpty()) {
            throw new IllegalArgumentException("Não foi possível identificar nenhum exercício na sua mensagem. Envie os exercícios com séries e repetições (ex: Supino 3x10 80kg)!");
        }
        if (dto.getDurationMinutes() != null) {
            int existingDuration = session.getDurationMinutes() != null ? session.getDurationMinutes() : 0;
            session.setDurationMinutes(existingDuration + dto.getDurationMinutes());
        }
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        existingExercises.forEach(this::ensureExerciseId);
        int startIndex = existingExercises.size();
        List<WorkoutDto.ExerciseDto> added = dto.getExercises();
        added.forEach(this::ensureExerciseId);
        existingExercises.addAll(added);
        session.setExercisesJson(serializeExercises(existingExercises));
        if (userMessageId != null) {
            session.setUserMessageId(userMessageId);
        }
        return new WorkoutUpdateResult(workoutRepository.save(session), startIndex, added);
    }

    public WorkoutSession editExercise(Long workoutId, int exerciseIndex, String text, byte[] audioBytes) {
        WorkoutSession session = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        WorkoutDto dto = geminiService.parseWorkout(text, audioBytes);
        if (dto == null || dto.getExercises() == null || dto.getExercises().isEmpty()) {
            throw new IllegalArgumentException("Não foi possível identificar o exercício atualizado. Tente descrever novamente com séries e peso!");
        }
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        if (exerciseIndex >= 0 && exerciseIndex < existingExercises.size()) {
            WorkoutDto.ExerciseDto oldEx = existingExercises.get(exerciseIndex);
            WorkoutDto.ExerciseDto newEx = dto.getExercises().get(0);
            newEx.setId(oldEx.getId() != null && !oldEx.getId().isBlank() ? oldEx.getId() : generateExerciseId());
            existingExercises.set(exerciseIndex, newEx);
        }
        session.setExercisesJson(serializeExercises(existingExercises));
        return workoutRepository.save(session);
    }

    @Override
    public WorkoutSession editExerciseById(Long workoutId, String exerciseId, String text, byte[] audioBytes) {
        WorkoutSession session = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        int idx = findExerciseIndex(existingExercises, exerciseId);
        if (idx < 0) {
            throw new IllegalArgumentException("Este exercício não foi encontrado ou já foi removido.");
        }
        WorkoutDto dto = geminiService.parseWorkout(text, audioBytes);
        if (dto == null || dto.getExercises() == null || dto.getExercises().isEmpty()) {
            throw new IllegalArgumentException("Não foi possível identificar o exercício atualizado. Tente descrever novamente com séries e peso!");
        }
        WorkoutDto.ExerciseDto oldEx = existingExercises.get(idx);
        WorkoutDto.ExerciseDto newEx = dto.getExercises().get(0);
        newEx.setId(oldEx.getId() != null && !oldEx.getId().isBlank() ? oldEx.getId() : generateExerciseId());
        existingExercises.set(idx, newEx);
        session.setExercisesJson(serializeExercises(existingExercises));
        return workoutRepository.save(session);
    }

    @Transactional
    public WorkoutSession removeExercise(Long workoutId, int exerciseIndex) {
        WorkoutSession session = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        if (exerciseIndex >= 0 && exerciseIndex < existingExercises.size()) {
            existingExercises.remove(exerciseIndex);
        }
        session.setExercisesJson(serializeExercises(existingExercises));
        return workoutRepository.save(session);
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

    private void ensureExerciseId(WorkoutDto.ExerciseDto ex) {
        if (ex != null && (ex.getId() == null || ex.getId().isBlank())) {
            ex.setId(generateExerciseId());
        }
    }

    private String generateExerciseId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Transactional
    public void saveBotMessageId(Long workoutId, Integer botMessageId) {
        workoutRepository.findById(workoutId).ifPresent(session -> {
            session.setBotMessageId(botMessageId);
            workoutRepository.save(session);
        });
    }

    public void deleteWorkout(Long workoutId) {
        workoutRepository.deleteById(workoutId);
    }

    public Optional<WorkoutSession> findById(Long workoutId) {
        return workoutRepository.findById(workoutId);
    }

    public boolean existeWorkout(Long workoutId) {
        return workoutRepository.existsById(workoutId);
    }

    public List<WorkoutSession> getWorkoutsForDate(UserTelegram user, java.time.LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(java.time.LocalTime.MAX);
        return workoutRepository.findByUserAndCreatedAtBetweenOrderByIdAsc(user, start, end);
    }

    public Optional<WorkoutSession> getLatestWorkoutForToday(UserTelegram user) {
        LocalDateTime start = java.time.LocalDate.now().atStartOfDay();
        LocalDateTime end = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);
        return workoutRepository.findFirstByUserAndCreatedAtBetweenOrderByCreatedAtDesc(user, start, end);
    }

    public List<WorkoutSession> getWorkoutsForToday(UserTelegram user) {
        return getWorkoutsForDate(user, java.time.LocalDate.now());
    }

    private String serializeExercises(List<WorkoutDto.ExerciseDto> exercises) {
        try {
            return objectMapper.writeValueAsString(exercises);
        } catch (Exception e) {
            return "[]";
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
            return new java.util.ArrayList<>();
        }
    }
}
