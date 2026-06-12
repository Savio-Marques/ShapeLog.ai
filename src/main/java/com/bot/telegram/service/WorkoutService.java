package com.bot.telegram.service;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WorkoutService implements IWorkoutService {

    private final WorkoutSessionRepository workoutRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public WorkoutService(GeminiService geminiService, WorkoutSessionRepository workoutRepository, ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.workoutRepository = workoutRepository;
        this.objectMapper = objectMapper;
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
        }
        return workoutRepository.save(session);
    }

    @Transactional
    public WorkoutUpdateResult addExercisesToDraft(Long workoutId, String text, byte[] audioBytes, Integer userMessageId) {
        WorkoutSession session = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        WorkoutDto dto = geminiService.parseWorkout(text, audioBytes);
        if (dto.getDurationMinutes() != null) {
            int existingDuration = session.getDurationMinutes() != null ? session.getDurationMinutes() : 0;
            session.setDurationMinutes(existingDuration + dto.getDurationMinutes());
        }
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        int startIndex = existingExercises.size();
        List<WorkoutDto.ExerciseDto> added = dto.getExercises() != null ? dto.getExercises() : java.util.List.of();
        existingExercises.addAll(added);
        session.setExercisesJson(serializeExercises(existingExercises));
        if (userMessageId != null) {
            session.setUserMessageId(userMessageId);
        }
        return new WorkoutUpdateResult(workoutRepository.save(session), startIndex, added);
    }

    @Transactional
    public WorkoutSession editExercise(Long workoutId, int exerciseIndex, String text, byte[] audioBytes) {
        WorkoutSession session = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Treino não encontrado com ID: " + workoutId));
        WorkoutDto dto = geminiService.parseWorkout(text, audioBytes);
        List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(session.getExercisesJson());
        if (exerciseIndex >= 0 && exerciseIndex < existingExercises.size()) {
            if (dto.getExercises() != null && !dto.getExercises().isEmpty()) {
                existingExercises.set(exerciseIndex, dto.getExercises().get(0));
            }
        }
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
