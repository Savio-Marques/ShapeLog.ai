package com.bot.telegram.service;
import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.DailyReportDto.ExerciseSummaryDto;
import com.bot.telegram.dto.DailyReportDto.ExerciseSummaryDto.SeriesSummaryDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {

    private final MealService mealService;
    private final WorkoutService workoutService;
    private final ObjectMapper objectMapper;

    public ReportService(MealService mealService, WorkoutService workoutService, ObjectMapper objectMapper) {
        this.mealService = mealService;
        this.workoutService = workoutService;
        this.objectMapper = objectMapper;
    }
    public DailyReportDto getReportForDate(UserTelegram user, java.time.LocalDate date) {
        List<Meal> meals = mealService.getMealsForDate(user, date);
        List<WorkoutSession> workouts = workoutService.getWorkoutsForDate(user, date);
        Integer totalCal = 0;
        Double totalProt = 0.0;
        Double totalCarb = 0.0;
        Double totalFat = 0.0;
        for (Meal meal : meals) {
            totalCal += meal.getCalories() != null ? meal.getCalories() : 0;
            totalProt += meal.getProtein() != null ? meal.getProtein() : 0;
            totalCarb += meal.getCarbs() != null ? meal.getCarbs() : 0;
            totalFat += meal.getFat() != null ? meal.getFat() : 0;
        }
        String workoutDescription = resolveWorkoutDescription(workouts);
        List<ExerciseSummaryDto> mergedExercises = mergeExercises(workouts);
        return DailyReportDto.builder()
                .meals(meals)
                .workouts(workouts)
                .totalCalories(totalCal)
                .totalProtein(totalProt)
                .totalCarbs(totalCarb)
                .totalFat(totalFat)
                .user(user)
                .workoutDescription(workoutDescription)
                .mergedExercises(mergedExercises)
                .build();
    }

    public DailyReportDto getDailyReport(UserTelegram user) {
        return getReportForDate(user, java.time.LocalDate.now());
    }

    private String resolveWorkoutDescription(List<WorkoutSession> workouts) {
        String bestDesc = "Geral";
        for (WorkoutSession w : workouts) {
            String desc = w.getRawInput() != null && !w.getRawInput().startsWith("[")
                    ? w.getRawInput()
                    : w.getDescription();
            if (desc == null || desc.trim().isEmpty()) continue;
            String trimmed = desc.trim();
            boolean isGeneric = "Geral".equalsIgnoreCase(trimmed) || "Treino".equalsIgnoreCase(trimmed);
            boolean currentIsGeneric = "Geral".equalsIgnoreCase(bestDesc) || "Treino".equalsIgnoreCase(bestDesc);
            if (!isGeneric || currentIsGeneric) {
                bestDesc = trimmed;
            }
        }
        return bestDesc;
    }

    private List<ExerciseSummaryDto> mergeExercises(List<WorkoutSession> workouts) {
        List<ExerciseSummaryDto> merged = new ArrayList<>();
        for (WorkoutSession w : workouts) {
            List<WorkoutDto.ExerciseDto> exercises = deserializeExercises(w.getExercisesJson());
            for (WorkoutDto.ExerciseDto newEx : exercises) {
                ExerciseSummaryDto match = merged.stream()
                        .filter(e -> e.getName() != null && newEx.getName() != null
                                && e.getName().trim().equalsIgnoreCase(newEx.getName().trim()))
                        .findFirst()
                        .orElse(null);
                if (match != null) {
                    List<SeriesSummaryDto> allSeries = new ArrayList<>(match.getSeries());
                    if (newEx.getSeries() != null) {
                        for (WorkoutDto.SeriesDto s : newEx.getSeries()) {
                            allSeries.add(SeriesSummaryDto.builder()
                                    .reps(s.getReps())
                                    .weight(s.getWeight())
                                    .build());
                        }
                    }
                    merged.set(merged.indexOf(match), ExerciseSummaryDto.builder()
                            .name(match.getName())
                            .series(allSeries)
                            .build());
                } else {
                    List<SeriesSummaryDto> series = new ArrayList<>();
                    if (newEx.getSeries() != null) {
                        for (WorkoutDto.SeriesDto s : newEx.getSeries()) {
                            series.add(SeriesSummaryDto.builder()
                                    .reps(s.getReps())
                                    .weight(s.getWeight())
                                    .build());
                        }
                    }
                    merged.add(ExerciseSummaryDto.builder()
                            .name(newEx.getName())
                            .series(series)
                            .build());
                }
            }
        }
        return merged;
    }

    private List<WorkoutDto.ExerciseDto> deserializeExercises(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<WorkoutDto.ExerciseDto>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
