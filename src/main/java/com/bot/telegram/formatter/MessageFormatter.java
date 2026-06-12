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
                "🥗 *Itens:* %s\n" +
                "📊 *Macros:* `%d kcal` | *P:* %sg | *C:* %sg | *G:* %sg",
                meal.getDescription() != null ? meal.getDescription() : "sem descrição",
                meal.getCalories() != null ? meal.getCalories() : 0,
                formatDouble(meal.getProtein()),
                formatDouble(meal.getCarbs()),
                formatDouble(meal.getFat())
        );
    }

    public String formatWorkoutRegistered(WorkoutSession session) {
        List<WorkoutDto.ExerciseDto> exercises = deserializeExercises(session.getExercisesJson());
        StringBuilder sb = new StringBuilder();
        sb.append("🏋️\u200d♂️ *Treino Registrado*\n\n");

        String desc = (session.getDescription() == null || session.getDescription().trim().isEmpty()) ? "Geral" : session.getDescription().trim();
        sb.append("*Treino:* ").append(desc).append("\n\n");

        if (exercises != null) {
            for (WorkoutDto.ExerciseDto ex : exercises) {
                sb.append("*").append(ex.getName()).append(":*\n");
                List<WorkoutDto.SeriesDto> series = ex.getSeries();
                if (series != null) {
                    for (int i = 0; i < series.size(); i++) {
                        WorkoutDto.SeriesDto s = series.get(i);
                        sb.append(String.format("\\* %dª série: %d reps — %s kg\n",
                                (i + 1),
                                s.getReps() != null ? s.getReps() : 0,
                                formatWeight(s.getWeight())
                        ));
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    public String formatDailyReport(DailyReportDto report) {
        return formatDailyReport(report, java.time.LocalDate.now());
    }

    public String formatDailyReport(DailyReportDto report, java.time.LocalDate date) {
        StringBuilder mealsList = new StringBuilder();
        int mealIdx = 1;
        for (Meal meal : report.getMeals()) {
            mealsList.append(String.format("• *Refeição %d:* %s\n", mealIdx++, meal.getDescription()));
            mealsList.append(String.format("  └ `%d kcal` | *P:* %sg | *C:* %sg | *G:* %sg\n\n",
                    meal.getCalories() != null ? meal.getCalories() : 0,
                    formatDouble(meal.getProtein()),
                    formatDouble(meal.getCarbs()),
                    formatDouble(meal.getFat())
            ));
        }
        if (report.getMeals().isEmpty()) {
            mealsList.append("Nenhuma refeição registrada hoje.\n");
        }

        StringBuilder workoutsList = new StringBuilder();
        List<WorkoutDto.ExerciseDto> allExercises = new java.util.ArrayList<>();
        String bestDesc = "Geral";

        for (WorkoutSession w : report.getWorkouts()) {
            String desc = w.getDescription();
            if (desc != null && !desc.trim().isEmpty() && !"Geral".equalsIgnoreCase(desc.trim()) && !"Treino".equalsIgnoreCase(desc.trim())) {
                bestDesc = desc.trim();
            } else if (("Geral".equals(bestDesc) || "Treino".equals(bestDesc)) && desc != null && !desc.trim().isEmpty()) {
                bestDesc = desc.trim();
            }

            List<WorkoutDto.ExerciseDto> exercises = deserializeExercises(w.getExercisesJson());
            if (exercises != null) {
                allExercises.addAll(exercises);
            }
        }

        List<WorkoutDto.ExerciseDto> uniqueExercises = new java.util.ArrayList<>();
        for (WorkoutDto.ExerciseDto newEx : allExercises) {
            WorkoutDto.ExerciseDto match = null;
            for (WorkoutDto.ExerciseDto ex : uniqueExercises) {
                if (ex.getName() != null && newEx.getName() != null && ex.getName().trim().equalsIgnoreCase(newEx.getName().trim())) {
                    match = ex;
                    break;
                }
            }
            if (match != null) {
                List<WorkoutDto.SeriesDto> matchSeries = match.getSeries();
                if (matchSeries == null) {
                    matchSeries = new java.util.ArrayList<>();
                    match.setSeries(matchSeries);
                } else {
                    matchSeries = new java.util.ArrayList<>(matchSeries);
                    match.setSeries(matchSeries);
                }
                if (newEx.getSeries() != null) {
                    matchSeries.addAll(newEx.getSeries());
                }
            } else {
                uniqueExercises.add(newEx);
            }
        }

        if (uniqueExercises.isEmpty()) {
            workoutsList.append("Nenhum treino registrado hoje.\n");
        } else {
            workoutsList.append("*Treino:* ").append(bestDesc).append("\n\n");
            for (WorkoutDto.ExerciseDto ex : uniqueExercises) {
                workoutsList.append("*").append(ex.getName()).append(":*\n");
                List<WorkoutDto.SeriesDto> series = ex.getSeries();
                if (series != null) {
                    for (int i = 0; i < series.size(); i++) {
                        WorkoutDto.SeriesDto s = series.get(i);
                        workoutsList.append(String.format("\\* %dª série: %d reps — %s kg\n",
                                (i + 1),
                                s.getReps() != null ? s.getReps() : 0,
                                formatWeight(s.getWeight())
                        ));
                    }
                }
                workoutsList.append("\n");
            }
        }

        UserTelegram user = report.getUser();
        int targetCal = (user.getTargetCalories() != null && user.getTargetCalories() > 0) ? user.getTargetCalories() : 2000;
        int targetProt = (user.getTargetProtein() != null && user.getTargetProtein() > 0) ? user.getTargetProtein() : 150;
        int targetCarb = (user.getTargetCarbs() != null && user.getTargetCarbs() > 0) ? user.getTargetCarbs() : 200;
        int targetFat = (user.getTargetFat() != null && user.getTargetFat() > 0) ? user.getTargetFat() : 60;

        int totalCal = report.getTotalCalories();
        double totalProt = report.getTotalProtein();
        double totalCarb = report.getTotalCarbs();
        double totalFat = report.getTotalFat();

        java.time.LocalDate today = java.time.LocalDate.now();
        String dateHeader;
        if (date.equals(today)) {
            dateHeader = "HOJE";
        } else if (date.equals(today.minusDays(1))) {
            dateHeader = "ONTEM";
        } else {
            dateHeader = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }

        return String.format(
                "📆 *RELATÓRIO DE %s*\n\n" +
                "🥗 *REFEIÇÕES*\n%s\n\n" +
                "🏋️\u200d♂️ *TREINOS*\n%s\n\n" +
                "📊 *TOTAIS VS. METAS*\n" +
                "*Calorias:* %d / %d kcal\n" +
                "*Proteínas:* %s / %dg\n" +
                "*Carbos:* %s / %dg\n" +
                "*Gorduras:* %s / %dg",
                dateHeader,
                mealsList.toString().trim(),
                workoutsList.toString().trim(),
                totalCal, targetCal,
                formatDouble(totalProt), targetProt,
                formatDouble(totalCarb), targetCarb,
                formatDouble(totalFat), targetFat
        );
    }

    private String formatDouble(Double value) {
        if (value == null) return "0,0";
        return String.format("%.1f", value).replace('.', ',');
    }

    private String formatWeight(Double value) {
        if (value == null) return "0";
        if (value == value.longValue()) {
            return String.format("%d", value.longValue());
        }
        return String.format("%.1f", value).replace('.', ',');
    }

    private List<WorkoutDto.ExerciseDto> deserializeExercises(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkoutDto.ExerciseDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
