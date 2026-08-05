package com.bot.telegram.service;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private MealService mealService;
    private WorkoutService workoutService;
    private ObjectMapper objectMapper;
    private ReportService reportService;

    private UserTelegram user;

    @BeforeEach
    void setUp() {
        mealService = mock(MealService.class);
        workoutService = mock(WorkoutService.class);
        objectMapper = new ObjectMapper();
        reportService = new ReportService(mealService, workoutService, objectMapper);

        user = UserTelegram.builder()
                .id(300L)
                .firstName("Sávio")
                .targetCalories(2000)
                .targetProtein(150)
                .targetCarbs(200)
                .targetFat(60)
                .build();
    }

    @Test
    void getReportForDate_deveSomarCaloriasEMacrosDasRefeicoes() {
        LocalDate hoje = LocalDate.now();

        Meal m1 = Meal.builder().calories(500).protein(30.0).carbs(50.0).fat(15.0).build();
        Meal m2 = Meal.builder().calories(700).protein(40.0).carbs(70.0).fat(20.0).build();

        when(mealService.getMealsForDate(user, hoje)).thenReturn(List.of(m1, m2));
        when(workoutService.getWorkoutsForDate(user, hoje)).thenReturn(List.of());

        DailyReportDto report = reportService.getReportForDate(user, hoje);
        assertNotNull(report);
        assertEquals(1200, report.getTotalCalories());
        assertEquals(70.0, report.getTotalProtein());
        assertEquals(120.0, report.getTotalCarbs());
        assertEquals(35.0, report.getTotalFat());
    }

    @Test
    void getReportForDate_semRefeicoes_deveRetornarZeros() {
        LocalDate hoje = LocalDate.now();
        when(mealService.getMealsForDate(user, hoje)).thenReturn(List.of());
        when(workoutService.getWorkoutsForDate(user, hoje)).thenReturn(List.of());

        DailyReportDto report = reportService.getReportForDate(user, hoje);
        assertNotNull(report);
        assertEquals(0, report.getTotalCalories());
        assertEquals(0.0, report.getTotalProtein());
        assertEquals(0.0, report.getTotalCarbs());
        assertEquals(0.0, report.getTotalFat());
    }

    @Test
    void getReportForDate_comTreino_deveIncluirNaLista() {
        LocalDate hoje = LocalDate.now();
        WorkoutSession ws = WorkoutSession.builder().rawInput("Peito e Tríceps").exercisesJson("[]").build();

        when(mealService.getMealsForDate(user, hoje)).thenReturn(List.of());
        when(workoutService.getWorkoutsForDate(user, hoje)).thenReturn(List.of(ws));

        DailyReportDto report = reportService.getReportForDate(user, hoje);
        assertNotNull(report);
        assertEquals(1, report.getWorkouts().size());
        assertEquals("Peito e Tríceps", report.getWorkouts().get(0).getRawInput());
    }
}
