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
    void testMessageFormatterDailyReport() {
        MessageFormatter formatter = new MessageFormatter();

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
                .description("Peito")
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
        assertTrue(formatted.contains("*Supino Inclinado:*"));
        assertTrue(formatted.contains("\\* 1ª série: 12 reps — 80 kg"));
        assertTrue(formatted.contains("\\* 2ª série: 8 reps — 100 kg"));
        assertTrue(formatted.contains("\\* 3ª série: 8 reps — 100 kg"));
        assertTrue(formatted.contains("*Supino Reto:*"));
        assertTrue(formatted.contains("\\* 1ª série: 10 reps — 40 kg"));
        assertTrue(formatted.contains("\\* 2ª série: 8 reps — 70 kg"));
        assertTrue(formatted.contains("| *Calorias* | 2752 / 2600 kcal"));
        assertTrue(formatted.contains("| *Proteínas* | 344,5 / 191g"));
        assertTrue(formatted.contains("| *Carbos* | 184,5 / 200g"));
        assertTrue(formatted.contains("| *Gorduras* | 64,5 / 82g"));
    }

    @Test
    void testWorkoutMergingLogic() {
        WorkoutSessionRepository workoutRepository = mock(WorkoutSessionRepository.class);
        GeminiService geminiService = mock(GeminiService.class);
        WorkoutService workoutService = new WorkoutService(workoutRepository, geminiService);

        UserTelegram user = UserTelegram.builder()
                .id(123456L)
                .firstName("Sávio")
                .build();

        // 1st parsed workout DTO
        WorkoutDto firstDto = WorkoutDto.builder()
                .description("Peito")
                .durationMinutes(30)
                .exercises(new java.util.ArrayList<>(List.of(
                        WorkoutDto.ExerciseDto.builder()
                                .name("Supino Inclinado")
                                .series(new java.util.ArrayList<>(List.of(
                                        WorkoutDto.SeriesDto.builder().reps(12).weight(80.0).build()
                                )))
                                .build()
                )))
                .build();

        // 2nd parsed workout DTO
        WorkoutDto secondDto = WorkoutDto.builder()
                .description("Geral")
                .durationMinutes(20)
                .exercises(new java.util.ArrayList<>(List.of(
                        WorkoutDto.ExerciseDto.builder()
                                .name("Supino Inclinado")
                                .series(new java.util.ArrayList<>(List.of(
                                        WorkoutDto.SeriesDto.builder().reps(8).weight(100.0).build()
                                )))
                                .build(),
                        WorkoutDto.ExerciseDto.builder()
                                .name("Supino Reto")
                                .series(new java.util.ArrayList<>(List.of(
                                        WorkoutDto.SeriesDto.builder().reps(10).weight(40.0).build()
                                )))
                                .build()
                )))
                .build();

        when(geminiService.parseWorkout(eq("treino 1"), any())).thenReturn(firstDto);
        when(geminiService.parseWorkout(eq("treino 2"), any())).thenReturn(secondDto);

        // When saving, just return the saved entity
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // First registration: no today session
        when(workoutRepository.findByUserAndCreatedAtBetween(eq(user), any(), any())).thenReturn(Collections.emptyList());

        WorkoutSession session1 = workoutService.registerWorkout(user, "treino 1", null);
        assertNotNull(session1);
        assertEquals("Peito", session1.getDescription());
        assertEquals(30, session1.getDurationMinutes());
        assertTrue(session1.getExercisesJson().contains("Supino Inclinado"));

        // Second registration: existing session today
        when(workoutRepository.findByUserAndCreatedAtBetween(eq(user), any(), any())).thenReturn(List.of(session1));

        WorkoutSession mergedSession = workoutService.registerWorkout(user, "treino 2", null);
        assertNotNull(mergedSession);
        // The description should stay "Peito" because "Geral" is not specific
        assertEquals("Peito", mergedSession.getDescription());
        // Duration should be aggregated (30 + 20)
        assertEquals(50, mergedSession.getDurationMinutes());
        // Exercises list: "Supino Inclinado" should merge series (reps 12 and reps 8), and "Supino Reto" should be added
        assertTrue(mergedSession.getExercisesJson().contains("Supino Reto"));
        
        System.out.println("--- MERGED EXERCISES JSON ---");
        System.out.println(mergedSession.getExercisesJson());
        System.out.println("-----------------------------");

        // Verify that the second series of Supino Inclinado was merged into the same exercise
        assertTrue(mergedSession.getExercisesJson().contains("80.0"));
        assertTrue(mergedSession.getExercisesJson().contains("100.0"));
    }
}
