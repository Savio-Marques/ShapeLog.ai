package com.bot.telegram.service;

import com.bot.telegram.dto.MealDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.MealRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MealService {

    private final MealRepository mealRepository;
    private final GeminiService geminiService;

    public MealService(MealRepository mealRepository, GeminiService geminiService) {
        this.mealRepository = mealRepository;
        this.geminiService = geminiService;
    }

    public Meal registerMeal(UserTelegram user, String text, byte[] audioBytes) {
        MealDto dto = geminiService.parseMeal(text, audioBytes);
        
        Meal meal = Meal.builder()
                .user(user)
                .rawInput(text != null ? text : "[Mensagem de Voz]")
                .description(dto.getDescription())
                .calories(dto.getCalories())
                .protein(dto.getProtein())
                .carbs(dto.getCarbs())
                .fat(dto.getFat())
                .createdAt(LocalDateTime.now())
                .build();
        
        return mealRepository.save(meal);
    }

    public List<Meal> getMealsForToday(UserTelegram user) {
        LocalDateTime start = LocalDateTime.now().with(java.time.LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now().with(java.time.LocalTime.MAX);
        return mealRepository.findByUserAndCreatedAtBetween(user, start, end);
    }
}
