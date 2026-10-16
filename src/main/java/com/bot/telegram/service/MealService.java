package com.bot.telegram.service;
import com.bot.telegram.dto.MealDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.MealRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealService {

    private final MealRepository mealRepository;
    private final GeminiService geminiService;
    private final MeterRegistry meterRegistry;

    public MealService(MealRepository mealRepository, GeminiService geminiService, MeterRegistry meterRegistry) {
        this.mealRepository = mealRepository;
        this.geminiService = geminiService;
        this.meterRegistry = meterRegistry;
    }

    public Meal registerMeal(UserTelegram user, String text, byte[] audioBytes) {
        return registerMeal(user, text, audioBytes, null);
    }

    public Meal registerMeal(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId) {
        MealDto dto = geminiService.parseMeal(text, audioBytes);
        validarMealDto(dto);
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
        Meal saved = mealRepository.save(meal);
        meterRegistry.counter("shapelog.meals.registered").increment();
        return saved;
    }

    public Meal updateMeal(Long mealId, String text, byte[] audioBytes) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new IllegalArgumentException("Refeição não encontrada com ID: " + mealId));
        MealDto dto = geminiService.parseMeal(text, audioBytes);
        validarMealDto(dto);
        meal.setRawInput(text != null ? text : "[Mensagem de Voz]");
        meal.setDescription(dto.getDescription() != null ? dto.getDescription() : "sem descrição");
        meal.setCalories(dto.getCalories() != null ? dto.getCalories() : 0);
        meal.setProtein(dto.getProtein() != null ? dto.getProtein() : 0.0);
        meal.setCarbs(dto.getCarbs() != null ? dto.getCarbs() : 0.0);
        meal.setFat(dto.getFat() != null ? dto.getFat() : 0.0);
        return mealRepository.save(meal);
    }

    private void validarMealDto(MealDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Não foi possível analisar a refeição. Tente descrever com mais detalhes!");
        }
        boolean semCalorias = dto.getCalories() == null || dto.getCalories() <= 0;
        boolean semProteina = dto.getProtein() == null || dto.getProtein() <= 0;
        boolean semCarbos   = dto.getCarbs() == null || dto.getCarbs() <= 0;
        boolean semGordura  = dto.getFat() == null || dto.getFat() <= 0;
        boolean semDescricao = dto.getDescription() == null || dto.getDescription().isBlank() || "sem descrição".equalsIgnoreCase(dto.getDescription().trim());

        if ((semCalorias && semProteina && semCarbos && semGordura) || semDescricao) {
            throw new IllegalArgumentException("Não foi possível identificar alimentos ou valores nutricionais válidos. Por favor, descreva sua refeição com mais detalhes!");
        }
    }

    @Transactional
    public void saveBotMessageId(Long mealId, Integer botMessageId) {
        mealRepository.findById(mealId).ifPresent(meal -> {
            meal.setBotMessageId(botMessageId);
            mealRepository.save(meal);
        });
    }

    public void deleteMeal(Long mealId) {
        mealRepository.deleteById(mealId);
    }

    public java.util.Optional<Meal> findById(Long mealId) {
        return mealRepository.findById(mealId);
    }

    public boolean existeMeal(Long mealId) {
        return mealRepository.existsById(mealId);
    }

    public List<Meal> getMealsForDate(UserTelegram user, java.time.LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(java.time.LocalTime.MAX);
        return mealRepository.findByUserAndCreatedAtBetweenOrderByIdAsc(user, start, end);
    }

    public List<Meal> getMealsForToday(UserTelegram user) {
        return getMealsForDate(user, java.time.LocalDate.now());
    }
}
