package com.bot.telegram.formatter;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.IWorkoutService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageFormatterTest {

    private IWorkoutService workoutService;
    private MessageFormatter messageFormatter;

    @BeforeEach
    void setUp() {
        workoutService = mock(IWorkoutService.class);
        messageFormatter = new MessageFormatter(workoutService);
    }

    @Test
    void formatStart_deveConterNomeDoUsuario() {
        String result = messageFormatter.formatStart("Sávio");
        assertNotNull(result);
        assertTrue(result.contains("Sávio"));
    }

    @Test
    void formatStart_deveConterTodosOsComandos() {
        String result = messageFormatter.formatStart("Sávio");
        assertTrue(result.contains("/refeicao"));
        assertTrue(result.contains("/treino"));
        assertTrue(result.contains("/exercicio"));
        assertTrue(result.contains("/meta"));
        assertTrue(result.contains("/relatorio"));
    }

    @Test
    void formatGoalsUpdated_deveFormatarValoresCorretamente() {
        String result = messageFormatter.formatGoalsUpdated(2500, 180, 250, 70);
        assertNotNull(result);
        assertTrue(result.contains("2500 kcal"));
        assertTrue(result.contains("180g"));
        assertTrue(result.contains("250g"));
        assertTrue(result.contains("70g"));
    }

    @Test
    void formatMealRegistered_deveExibirDescricaoEMacros() {
        Meal meal = Meal.builder()
                .description("Arroz, feijão e frango")
                .calories(600)
                .protein(45.0)
                .carbs(60.0)
                .fat(12.0)
                .build();

        String result = messageFormatter.formatMealRegistered(meal);
        assertNotNull(result);
        assertTrue(result.contains("Arroz, feijão e frango"));
        assertTrue(result.contains("600 kcal"));
        assertTrue(result.contains("45,0g"));
        assertTrue(result.contains("60,0g"));
        assertTrue(result.contains("12,0g"));
    }

    @Test
    void formatMealRegistered_deveTratarValoresNulos() {
        Meal meal = Meal.builder().build();
        String result = messageFormatter.formatMealRegistered(meal);
        assertNotNull(result);
        assertTrue(result.contains("sem descrição"));
        assertTrue(result.contains("0 kcal"));
    }

    @Test
    void formatDailyReport_deveMostrarHojeParaDataAtual() {
        UserTelegram user = UserTelegram.builder().firstName("Sávio").build();
        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of())
                .workouts(List.of())
                .user(user)
                .build();

        String result = messageFormatter.formatDailyReport(report, LocalDate.now());
        assertTrue(result.contains("RELATÓRIO DE HOJE"));
    }

    @Test
    void formatDailyReport_deveMostrarOntemParaDataAnterior() {
        UserTelegram user = UserTelegram.builder().firstName("Sávio").build();
        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of())
                .workouts(List.of())
                .user(user)
                .build();

        String result = messageFormatter.formatDailyReport(report, LocalDate.now().minusDays(1));
        assertTrue(result.contains("RELATÓRIO DE ONTEM"));
    }

    @Test
    void escapeMarkdown_deveEscaparCaracteresEspeciais() {
        String input = "Olá! Teste_1 * 2 [3] (4) ~ > # + - = | { } . !";
        String escaped = messageFormatter.escapeMarkdown(input);
        assertNotNull(escaped);
        assertTrue(escaped.contains("\\!"));
        assertTrue(escaped.contains("\\_"));
        assertTrue(escaped.contains("\\*"));
        assertTrue(escaped.contains("\\["));
        assertTrue(escaped.contains("\\]"));
        assertTrue(escaped.contains("\\."));
    }

    @Test
    void testMessageFormatterDailyReportMigrado() throws Exception {
        when(workoutService.deserializeExercises(anyString())).thenAnswer(inv -> {
            String json = inv.getArgument(0);
            if (json == null || json.isBlank() || json.equals("[]")) return List.of();
            ObjectMapper mapper = new ObjectMapper();
            return java.util.Arrays.asList(mapper.readValue(json, WorkoutDto.ExerciseDto[].class));
        });

        UserTelegram user = UserTelegram.builder()
                .firstName("Sávio")
                .targetCalories(2600)
                .targetProtein(191)
                .targetCarbs(200)
                .targetFat(82)
                .build();

        Meal meal = Meal.builder()
                .description("uma maçã média, 4 bananas")
                .calories(515)
                .protein(5.7)
                .carbs(133.0)
                .fat(1.9)
                .build();

        String exercisesJson = "[{\"name\":\"Supino Inclinado\",\"series\":[{\"reps\":12,\"weight\":80.0},{\"reps\":8,\"weight\":100.0}]},{\"name\":\"Supino Reto\",\"series\":[{\"reps\":10,\"weight\":40.0}]}]";

        WorkoutSession workout = WorkoutSession.builder()
                .rawInput("Peito")
                .exercisesJson(exercisesJson)
                .build();

        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of(meal))
                .workouts(List.of(workout))
                .totalCalories(2752)
                .totalProtein(344.5)
                .totalCarbs(184.5)
                .totalFat(64.5)
                .user(user)
                .build();

        String formatted = messageFormatter.formatDailyReport(report);
        assertNotNull(formatted);
        assertTrue(formatted.contains("Peito"));
        assertTrue(formatted.contains("Supino Inclinado"));
        assertTrue(formatted.contains("12 reps"));
        assertTrue(formatted.contains("80 kg"));
    }
}
