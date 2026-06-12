package com.bot.telegram.service;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import java.util.List;
import java.util.Optional;

public interface IWorkoutService {

    WorkoutService.WorkoutUpdateResult addExercisesToDraft(Long workoutId, String text, byte[] audioBytes, Integer userMessageId);
    WorkoutSession createOrUpdateDraftWorkout(UserTelegram user, String title, Integer userMessageId);
    WorkoutSession editExercise(Long workoutId, int exerciseIndex, String text, byte[] audioBytes);
    WorkoutSession removeExercise(Long workoutId, int exerciseIndex);
    void saveBotMessageId(Long workoutId, Integer botMessageId);
    void deleteWorkout(Long workoutId);
    Optional<WorkoutSession> findById(Long workoutId);
    boolean existeWorkout(Long workoutId);
    Optional<WorkoutSession> getLatestWorkoutForToday(UserTelegram user);
    List<WorkoutSession> getWorkoutsForDate(UserTelegram user, java.time.LocalDate date);
    List<WorkoutSession> getWorkoutsForToday(UserTelegram user);
    List<WorkoutDto.ExerciseDto> deserializeExercises(String json);
}
