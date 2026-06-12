package com.bot.telegram.dto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class DailyReportDto {

    private List<Meal> meals;
    private List<WorkoutSession> workouts;
    private Integer totalCalories;
    private Double totalProtein;
    private Double totalCarbs;
    private Double totalFat;
    private UserTelegram user;
    private List<ExerciseSummaryDto> mergedExercises;
    private String workoutDescription;

    @Getter
    @Builder
    public static class ExerciseSummaryDto {
        private String name;
        private List<SeriesSummaryDto> series;
        @Getter
        @Builder
        public static class SeriesSummaryDto {
            private Integer reps;
            private Double weight;
        }
    }
}
