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

    public DailyReportDto getDailyReport(UserTelegram user) {
        List<Meal> meals = mealService.getMealsForToday(user);
        List<WorkoutSession> workouts = workoutService.getWorkoutsForToday(user);

        int totalCal = 0;
        double totalProt = 0;
        double totalCarb = 0;
        double totalFat = 0;

        for (Meal meal : meals) {
            totalCal += meal.getCalories();
            totalProt += meal.getProtein();
            totalCarb += meal.getCarbs();
            totalFat += meal.getFat();
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
}
