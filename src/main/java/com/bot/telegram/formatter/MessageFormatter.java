package com.bot.telegram.formatter;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MessageFormatter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String formatStart(String firstName) {
        return "👋 Olá, *" + firstName + "*! Bem-vindo ao *ShapeLog.ai*.\n\n" +
                "Aqui você pode registrar sua alimentação e treinos diários por texto ou áudio!\n\n" +
                "📌 *Comandos Disponíveis:*\n" +
                "🥗 `/refeicao <alimentos>` - Registra uma refeição\n" +
                "🏋️‍♂️ `/treino <exercicios>` - Registra um treino\n" +
                "🎯 `/meta <calorias> <proteinas> <carbos> <gorduras>` - Define metas\n" +
                "📊 `/relatorio` - Exibe o progresso diário";
    }

    public String formatGoalsUpdated(int cal, int prot, int carb, int fat) {
        return String.format(
                "🎯 *Metas Diárias Atualizadas!*\n" +
                "🔥 *Calorias:* %d kcal\n" +
                "💪 *Proteínas:* %dg\n" +
                "🍞 *Carboidratos:* %dg\n" +
                "🥑 *Gorduras:* %dg",
                cal, prot, carb, fat
        );
    }

    public String formatMealRegistered(Meal meal) {
        return String.format(
                "✅ *Refeição Registrada!*\n\n" +
                "🥗 *Descrição:* %s\n" +
                "🔥 *Calorias:* %d kcal\n" +
                "💪 *Proteínas:* %.1fg\n" +
                "🍞 *Carboidratos:* %.1fg\n" +
                "🥑 *Gorduras:* %.1fg",
                meal.getDescription(),
                meal.getCalories(),
                meal.getProtein(),
                meal.getCarbs(),
                meal.getFat()
        );
    }

    public String formatWorkoutRegistered(WorkoutSession session) {
        List<WorkoutDto.ExerciseDto> exercises = deserializeExercises(session.getExercisesJson());
        StringBuilder exercisesStr = new StringBuilder();
        if (exercises != null) {
            for (WorkoutDto.ExerciseDto ex : exercises) {
                exercisesStr.append(String.format("• %s: %d séries x %s reps (%.1f kg)\n",
                        ex.getName(),
                        ex.getSets() != null ? ex.getSets() : 0,
                        ex.getReps() != null ? ex.getReps() : "N/A",
                        ex.getWeight() != null ? ex.getWeight() : 0.0
                ));
            }
        }

        return String.format(
                "🏋️‍♂️ *Treino Registrado!*\n\n" +
                "📝 *Descrição:* %s\n" +
                "⏱️ *Duração:* %d min\n\n" +
                "🏋️‍♂️ *Exercícios:*\n%s",
                session.getDescription(),
                session.getDurationMinutes(),
                exercisesStr.length() > 0 ? exercisesStr.toString() : "Nenhum exercício detalhado."
        );
    }

    public String formatDailyReport(DailyReportDto report) {
        StringBuilder mealsList = new StringBuilder();
        for (Meal meal : report.getMeals()) {
            mealsList.append(String.format("• %s (%d kcal) - P: %.1fg | C: %.1fg | G: %.1fg\n",
                    meal.getDescription(), meal.getCalories(), meal.getProtein(), meal.getCarbs(), meal.getFat()));
        }

        StringBuilder workoutsList = new StringBuilder();
        for (WorkoutSession w : report.getWorkouts()) {
            workoutsList.append(String.format("• %s (%d min)\n", w.getDescription(), w.getDurationMinutes()));
        }

        if (report.getMeals().isEmpty()) {
            mealsList.append("Nenhuma refeição registrada hoje.\n");
        }
        if (report.getWorkouts().isEmpty()) {
            workoutsList.append("Nenhum treino registrado hoje.\n");
        }

        UserTelegram user = report.getUser();
        int targetCal = user.getTargetCalories() > 0 ? user.getTargetCalories() : 2000;
        int targetProt = user.getTargetProtein() > 0 ? user.getTargetProtein() : 150;
        int targetCarb = user.getTargetCarbs() > 0 ? user.getTargetCarbs() : 200;
        int targetFat = user.getTargetFat() > 0 ? user.getTargetFat() : 60;

        return String.format(
                "📅 *RELATÓRIO DO DIA*\n\n" +
                "🥗 *Refeições:*\n%s\n" +
                "🏋️‍♂️ *Treinos:*\n%s\n" +
                "📊 *Totais do Dia vs. Metas:*\n" +
                "• *Calorias:* %d / %d kcal (%.1f%%)\n" +
                "• *Proteínas:* %.1f / %dg (%.1f%%)\n" +
                "• *Carbos:* %.1f / %dg (%.1f%%)\n" +
                "• *Gorduras:* %.1f / %dg (%.1f%%)",
                mealsList.toString(),
                workoutsList.toString(),
                report.getTotalCalories(), targetCal, (report.getTotalCalories() * 100.0 / targetCal),
                report.getTotalProtein(), targetProt, (report.getTotalProtein() * 100.0 / targetProt),
                report.getTotalCarbs(), targetCarb, (report.getTotalCarbs() * 100.0 / targetCarb),
                report.getTotalFat(), targetFat, (report.getTotalFat() * 100.0 / targetFat)
        );
    }

    private List<WorkoutDto.ExerciseDto> deserializeExercises(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkoutDto.ExerciseDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
