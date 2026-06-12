package com.bot.telegram;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.bot.telegram.service.GeminiService;
import com.bot.telegram.service.WorkoutService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TelegramApplicationTests {

    @Test
    void testMessageFormatterDailyReport() throws Exception {
        com.bot.telegram.service.IWorkoutService workoutService = mock(com.bot.telegram.service.IWorkoutService.class);
        when(workoutService.deserializeExercises(anyString())).thenAnswer(inv -> {
            String json = inv.getArgument(0);
            if (json == null || json.isBlank() || json.equals("[]")) return List.of();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return java.util.Arrays.asList(mapper.readValue(json, WorkoutDto.ExerciseDto[].class));
        });
        MessageFormatter formatter = new MessageFormatter(workoutService);

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

        // Mock workout JSON matching the user's request
        String exercisesJson = "[{\"name\":\"Supino Inclinado\",\"series\":[{\"reps\":12,\"weight\":80.0},{\"reps\":8,\"weight\":100.0},{\"reps\":8,\"weight\":100.0}]},{\"name\":\"Supino Reto\",\"series\":[{\"reps\":10,\"weight\":40.0},{\"reps\":8,\"weight\":70.0}]}]";

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

        String formatted = formatter.formatDailyReport(report);
        System.out.println("--- FORMATTED REPORT OUTPUT ---");
        System.out.println(formatted);
        System.out.println("--------------------------------");

        assertNotNull(formatted);
        assertTrue(formatted.contains("*Treino:* Peito"));
        assertTrue(formatted.contains("Supino Inclinado:"));
        assertTrue(formatted.contains("12 reps"));
        assertTrue(formatted.contains("80 kg"));
        assertTrue(formatted.contains("100 kg"));
        assertTrue(formatted.contains("Supino Reto"));
        assertTrue(formatted.contains("10 reps"));
        assertTrue(formatted.contains("40 kg"));
        assertTrue(formatted.contains("70 kg"));
        assertTrue(formatted.contains("*Calorias:* 2752 / 2600 kcal"));
        assertTrue(formatted.contains("*Proteínas:* 344,5 / 191g"));
        assertTrue(formatted.contains("*Carbos:* 184,5 / 200g"));
        assertTrue(formatted.contains("*Gorduras:* 64,5 / 82g"));
    }

    @Test
    void testMessageFormatterDynamicHeaders() {
        com.bot.telegram.service.IWorkoutService workoutService = mock(com.bot.telegram.service.IWorkoutService.class);
        MessageFormatter formatter = new MessageFormatter(workoutService);

        UserTelegram user = UserTelegram.builder()
                .firstName("Sávio")
                .build();

        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of())
                .workouts(List.of())
                .user(user)
                .build();

        // 1. Test Today
        String reportToday = formatter.formatDailyReport(report, java.time.LocalDate.now());
        assertTrue(reportToday.contains("📆 *RELATÓRIO DE HOJE*"));

        // 2. Test Yesterday
        String reportYesterday = formatter.formatDailyReport(report, java.time.LocalDate.now().minusDays(1));
        assertTrue(reportYesterday.contains("📆 *RELATÓRIO DE ONTEM*"));

        // 3. Test Specific Date
        String reportSpecific = formatter.formatDailyReport(report, java.time.LocalDate.of(2026, 6, 8));
        assertTrue(reportSpecific.contains("📆 *RELATÓRIO DE 08/06/2026*"));
    }

    @Test
    void testParseDateLogic() {
        java.time.LocalDate today = java.time.LocalDate.now();
        
        // Matcher for DD/MM/YYYY
        String input1 = "10/06/2026";
        assertTrue(input1.matches("\\d{2}/\\d{2}/\\d{4}"));
        java.time.LocalDate parsed1 = java.time.LocalDate.parse(input1, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertEquals(2026, parsed1.getYear());
        assertEquals(6, parsed1.getMonthValue());
        assertEquals(10, parsed1.getDayOfMonth());

        // Matcher for DD/MM
        String input2 = "12/04";
        assertTrue(input2.matches("\\d{2}/\\d{2}"));
        String fullDate = input2 + "/" + today.getYear();
        java.time.LocalDate parsed2 = java.time.LocalDate.parse(fullDate, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertEquals(today.getYear(), parsed2.getYear());
        assertEquals(4, parsed2.getMonthValue());
        assertEquals(12, parsed2.getDayOfMonth());
    }
}
