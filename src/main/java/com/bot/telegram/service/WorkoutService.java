package com.bot.telegram.service;

import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkoutService {

    private final WorkoutSessionRepository workoutRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkoutService(WorkoutSessionRepository workoutRepository, GeminiService geminiService) {
        this.workoutRepository = workoutRepository;
        this.geminiService = geminiService;
    }

    public WorkoutSession registerWorkout(UserTelegram user, String text, byte[] audioBytes) {
        WorkoutDto dto = geminiService.parseWorkout(text, audioBytes);
        
        List<WorkoutSession> todaySessions = getWorkoutsForToday(user);
        if (!todaySessions.isEmpty()) {
            WorkoutSession existingSession = todaySessions.get(0);
            
            // 1. Merge description if new one is specific (not null, not empty, and not "Geral")
            if (dto.getDescription() != null && !dto.getDescription().trim().isEmpty() && !"Geral".equalsIgnoreCase(dto.getDescription().trim())) {
                existingSession.setDescription(dto.getDescription().trim());
            } else if (existingSession.getDescription() == null || existingSession.getDescription().trim().isEmpty() || "Geral".equalsIgnoreCase(existingSession.getDescription().trim())) {
                if (dto.getDescription() != null && !dto.getDescription().trim().isEmpty()) {
                    existingSession.setDescription(dto.getDescription().trim());
                }
            }
            
            // 2. Add duration
            if (dto.getDurationMinutes() != null) {
                int existingDuration = existingSession.getDurationMinutes() != null ? existingSession.getDurationMinutes() : 0;
                existingSession.setDurationMinutes(existingDuration + dto.getDurationMinutes());
            }
            
            // 3. Merge exercises
            List<WorkoutDto.ExerciseDto> existingExercises = deserializeExercises(existingSession.getExercisesJson());
            if (dto.getExercises() != null) {
                for (WorkoutDto.ExerciseDto newEx : dto.getExercises()) {
                    WorkoutDto.ExerciseDto match = null;
                    for (WorkoutDto.ExerciseDto ex : existingExercises) {
                        if (ex.getName() != null && newEx.getName() != null && ex.getName().trim().equalsIgnoreCase(newEx.getName().trim())) {
                            match = ex;
                            break;
                        }
                    }
                    if (match != null) {
                        List<WorkoutDto.SeriesDto> matchSeries = match.getSeries();
                        if (matchSeries == null) {
                            matchSeries = new java.util.ArrayList<>();
                            match.setSeries(matchSeries);
                        } else {
                            matchSeries = new java.util.ArrayList<>(matchSeries);
                            match.setSeries(matchSeries);
                        }
                        if (newEx.getSeries() != null) {
                            matchSeries.addAll(newEx.getSeries());
                        }
                    } else {
                        existingExercises.add(newEx);
                    }
                }
            }
            
            existingSession.setExercisesJson(serializeExercises(existingExercises));
            
            String existingRaw = existingSession.getRawInput() != null ? existingSession.getRawInput() : "";
            String newRaw = text != null ? text : "[Mensagem de Voz]";
            existingSession.setRawInput(existingRaw + " | " + newRaw);
            
            return workoutRepository.save(existingSession);
        } else {
            WorkoutSession session = WorkoutSession.builder()
                    .user(user)
                    .rawInput(text != null ? text : "[Mensagem de Voz]")
                    .description(dto.getDescription())
                    .durationMinutes(dto.getDurationMinutes())
                    .exercisesJson(serializeExercises(dto.getExercises()))
                    .createdAt(LocalDateTime.now())
                    .build();
                
            return workoutRepository.save(session);
        }
    }

    public List<WorkoutSession> getWorkoutsForDate(UserTelegram user, java.time.LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(java.time.LocalTime.MAX);
        return workoutRepository.findByUserAndCreatedAtBetween(user, start, end);
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

    private List<WorkoutDto.ExerciseDto> deserializeExercises(String json) {
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
