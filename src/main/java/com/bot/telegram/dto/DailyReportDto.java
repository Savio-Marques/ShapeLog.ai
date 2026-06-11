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
    private int totalCalories;
    private double totalProtein;
    private double totalCarbs;
    private double totalFat;
    private UserTelegram user;
}
