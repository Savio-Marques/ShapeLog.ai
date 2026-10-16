package com.bot.telegram.service;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportService {

    private final MealService mealService;
    private final WorkoutService workoutService;

    public ReportService(MealService mealService, WorkoutService workoutService) {
        this.mealService = mealService;
        this.workoutService = workoutService;
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
        return DailyReportDto.builder()
                .meals(meals)
                .workouts(workouts)
                .totalCalories(totalCal)
                .totalProtein(totalProt)
                .totalCarbs(totalCarb)
                .totalFat(totalFat)
                .user(user)
                .build();
    }

    public DailyReportDto getDailyReport(UserTelegram user) {
        return getReportForDate(user, java.time.LocalDate.now());
    }
}
