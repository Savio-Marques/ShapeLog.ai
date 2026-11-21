package com.bot.telegram.service;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReportService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final MealService mealService;
    private final WorkoutService workoutService;
    private final ZoneId appZoneId;

    public ReportService(MealService mealService, WorkoutService workoutService) {
        this(mealService, workoutService, DEFAULT_ZONE);
    }

    @Autowired
    public ReportService(MealService mealService, WorkoutService workoutService, ZoneId appZoneId) {
        this.mealService = mealService;
        this.workoutService = workoutService;
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    public DailyReportDto getReportForDate(UserTelegram user, LocalDate date) {
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
        return getReportForDate(user, LocalDate.now(appZoneId));
    }
}
