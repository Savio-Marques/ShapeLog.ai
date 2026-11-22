package com.bot.telegram.service;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IWorkoutService {

    WorkoutService.WorkoutUpdateResult addExercisesOrCreateWorkout(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId);
    WorkoutSession createOrUpdateDraftWorkout(UserTelegram user, String title, Integer userMessageId);
    WorkoutSession createNewDraftWorkout(UserTelegram user, String title, Integer userMessageId);
    WorkoutSession editExerciseById(Long workoutId, String exerciseId, String text, byte[] audioBytes);
    WorkoutSession removeExerciseById(Long workoutId, String exerciseId);
    int findExerciseIndex(List<WorkoutDto.ExerciseDto> exercises, String idOrIndex);
    void deleteWorkout(Long workoutId);
    Optional<WorkoutSession> findByIdAndUserId(Long workoutId, Long userId);
    boolean existeWorkout(Long workoutId);
    Optional<WorkoutSession> getLatestWorkoutForToday(UserTelegram user);
    List<WorkoutSession> getWorkoutsForDate(UserTelegram user, LocalDate date);
    List<WorkoutDto.ExerciseDto> deserializeExercises(String json);
}
