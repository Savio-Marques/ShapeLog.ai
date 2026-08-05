package com.bot.telegram.service;

import com.bot.telegram.dto.MealDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.repository.MealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MealServiceTest {

    private MealRepository mealRepository;
    private GeminiService geminiService;
    private MealService mealService;

    private UserTelegram testUser;

    @BeforeEach
    void setUp() {
        mealRepository = mock(MealRepository.class);
        geminiService = mock(GeminiService.class);
        mealService = new MealService(mealRepository, geminiService);

        testUser = UserTelegram.builder()
                .id(200L)
                .firstName("Sávio")
                .approved(true)
                .build();
    }

    @Test
    void registerMeal_deveSalvarRefeicaoNoBanco() {
        MealDto dto = MealDto.builder()
                .description("2 ovos mexidos, 1 pão francês")
                .calories(350)
                .protein(18.0)
                .carbs(30.0)
                .fat(10.0)
                .build();
        when(geminiService.parseMeal(anyString(), any())).thenReturn(dto);
        when(mealRepository.save(any(Meal.class))).thenAnswer(inv -> {
            Meal m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        Meal savedMeal = mealService.registerMeal(testUser, "2 ovos e 1 pão", null, 10);
        assertNotNull(savedMeal);
        assertEquals(1L, savedMeal.getId());
        assertEquals("2 ovos mexidos, 1 pão francês", savedMeal.getDescription());
        assertEquals(350, savedMeal.getCalories());
        assertEquals(18.0, savedMeal.getProtein());
    }

    @Test
    void registerMeal_comAudio_deveSalvarRawInputComoMensagemDeVoz() {
        MealDto dto = MealDto.builder().description("Voz: 1 banana").calories(90).protein(1.0).carbs(22.0).fat(0.3).build();
        when(geminiService.parseMeal(any(), any())).thenReturn(dto);
        when(mealRepository.save(any(Meal.class))).thenAnswer(inv -> inv.getArgument(0));

        Meal savedMeal = mealService.registerMeal(testUser, null, new byte[]{1, 2, 3}, 11);
        assertEquals("[Mensagem de Voz]", savedMeal.getRawInput());
    }

    @Test
    void updateMeal_deveAtualizarDescricaoEMacros() {
        Meal mealExistente = Meal.builder().id(10L).user(testUser).description("Pão").calories(150).protein(4.0).carbs(28.0).fat(2.0).build();
        MealDto dtoNovo = MealDto.builder().description("Pão com manteiga").calories(250).protein(4.0).carbs(28.0).fat(12.0).build();

        when(mealRepository.findById(10L)).thenReturn(Optional.of(mealExistente));
        when(geminiService.parseMeal(anyString(), any())).thenReturn(dtoNovo);
        when(mealRepository.save(any(Meal.class))).thenAnswer(inv -> inv.getArgument(0));

        Meal mealAtualizada = mealService.updateMeal(10L, "Pão com manteiga", null);
        assertEquals("Pão com manteiga", mealAtualizada.getDescription());
        assertEquals(250, mealAtualizada.getCalories());
        assertEquals(12.0, mealAtualizada.getFat());
    }

    @Test
    void deleteMeal_deveRemoverDoBanco() {
        mealService.deleteMeal(10L);
        verify(mealRepository).deleteById(10L);
    }

    @Test
    void getMealsForDate_deveRetornarApenasDoDiaCorreto() {
        Meal m = Meal.builder().id(1L).description("Almoço").build();
        when(mealRepository.findByUserAndCreatedAtBetweenOrderByIdAsc(eq(testUser), any(), any())).thenReturn(List.of(m));

        List<Meal> meals = mealService.getMealsForDate(testUser, LocalDate.now());
        assertEquals(1, meals.size());
    }
}
