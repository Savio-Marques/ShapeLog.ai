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

    public List<WorkoutSession> getWorkoutsForToday(UserTelegram user) {
        LocalDateTime start = LocalDateTime.now().with(java.time.LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now().with(java.time.LocalTime.MAX);
        return workoutRepository.findByUserAndCreatedAtBetween(user, start, end);
    }

    private String serializeExercises(List<WorkoutDto.ExerciseDto> exercises) {
        try {
            return objectMapper.writeValueAsString(exercises);
        } catch (Exception e) {
            return "[]";
        }
    }
}
