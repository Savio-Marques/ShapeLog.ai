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
        return registerMeal(user, text, audioBytes, null);
    }

    public Meal registerMeal(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId) {
        MealDto dto = geminiService.parseMeal(text, audioBytes);
        
        Meal meal = Meal.builder()
                .user(user)
                .rawInput(text != null ? text : "[Mensagem de Voz]")
                .description(dto.getDescription() != null ? dto.getDescription() : "sem descrição")
                .calories(dto.getCalories() != null ? dto.getCalories() : 0)
                .protein(dto.getProtein() != null ? dto.getProtein() : 0.0)
                .carbs(dto.getCarbs() != null ? dto.getCarbs() : 0.0)
                .fat(dto.getFat() != null ? dto.getFat() : 0.0)
                .userMessageId(userMessageId)
                .createdAt(LocalDateTime.now())
                .build();
        
        return mealRepository.save(meal);
    }

    public Meal updateMeal(Long mealId, String text, byte[] audioBytes) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Refeição não encontrada com ID: " + mealId));
        
        MealDto dto = geminiService.parseMeal(text, audioBytes);
        
        meal.setRawInput(text != null ? text : "[Mensagem de Voz]");
        meal.setDescription(dto.getDescription() != null ? dto.getDescription() : "sem descrição");
        meal.setCalories(dto.getCalories() != null ? dto.getCalories() : 0);
        meal.setProtein(dto.getProtein() != null ? dto.getProtein() : 0.0);
        meal.setCarbs(dto.getCarbs() != null ? dto.getCarbs() : 0.0);
        meal.setFat(dto.getFat() != null ? dto.getFat() : 0.0);
        
        return mealRepository.save(meal);
    }

    public void saveBotMessageId(Long mealId, Integer botMessageId) {
        mealRepository.findById(mealId).ifPresent(meal -> {
            meal.setBotMessageId(botMessageId);
            mealRepository.save(meal);
        });
    }

    public void deleteMeal(Long mealId) {
        mealRepository.deleteById(mealId);
    }

    // Item 7: verificar existência antes de deletar para tratar duplo clique graciosamente
    public boolean existeMeal(Long mealId) {
        return mealRepository.existsById(mealId);
    }


    public List<Meal> getMealsForDate(UserTelegram user, java.time.LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(java.time.LocalTime.MAX);
        return mealRepository.findByUserAndCreatedAtBetween(user, start, end);
    }

    public List<Meal> getMealsForToday(UserTelegram user) {
        return getMealsForDate(user, java.time.LocalDate.now());
    }
}
