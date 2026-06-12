package com.bot.telegram.formatter;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.DailyReportDto.ExerciseSummaryDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Responsável exclusivamente por formatar dados em strings para o Telegram.
 * Não contém lógica de negócio — recebe dados já processados e apenas os apresenta.
 */
@Component
public class MessageFormatter {

    private final ObjectMapper objectMapper;

    public MessageFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("`", "\\`")
                   .replace("[", "\\[");
    }

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
                meal.getDescription() != null ? escapeMarkdown(meal.getDescription()) : "sem descrição",
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

        String desc = session.getDescription() != null ? escapeMarkdown(session.getDescription()) : "Geral";
        sb.append("*Treino:* ").append(desc).append("\n\n");

        if (exercises != null) {
            for (WorkoutDto.ExerciseDto ex : exercises) {
                sb.append("🔹 ").append(escapeMarkdown(ex.getName())).append(":\n");
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

    public String formatExercisesAdded(List<WorkoutDto.ExerciseDto> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return "Nenhum exercício reconhecido.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Exercício Registrado*\n\n");
        for (WorkoutDto.ExerciseDto ex : exercises) {
            sb.append("🔹 ").append(escapeMarkdown(ex.getName())).append(":\n");
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
        return sb.toString().trim();
    }

    public String formatDailyReport(DailyReportDto report) {
        return formatDailyReport(report, java.time.LocalDate.now());
    }

    public String formatDailyReport(DailyReportDto report, java.time.LocalDate date) {
        // — Refeições —
        StringBuilder mealsList = new StringBuilder();
        int mealIdx = 1;
        for (Meal meal : report.getMeals()) {
            mealsList.append(String.format("• *Refeição %d:* %s\n", mealIdx++, escapeMarkdown(meal.getDescription())));
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

        // — Treinos —
        StringBuilder workoutsList = new StringBuilder();
        if (report.getWorkouts() == null || report.getWorkouts().isEmpty()) {
            workoutsList.append("Nenhum treino registrado hoje.\n");
        } else {
            for (WorkoutSession workout : report.getWorkouts()) {
                workoutsList.append("Treino: ").append(escapeMarkdown(workout.getRawInput() != null && !workout.getRawInput().startsWith("[") ? workout.getRawInput() : "Treino")).append("\n\n");
                
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    WorkoutDto.ExerciseDto[] exs = mapper.readValue(workout.getExercisesJson(), WorkoutDto.ExerciseDto[].class);
                    for (WorkoutDto.ExerciseDto ex : exs) {
                        workoutsList.append("🔹 ").append(escapeMarkdown(ex.getName())).append(":\n");
                        if (ex.getSeries() != null) {
                            for (int i = 0; i < ex.getSeries().size(); i++) {
                                var s = ex.getSeries().get(i);
                                workoutsList.append(String.format("\\* %dª série: %d reps — %s kg\n",
                                        (i + 1),
                                        s.getReps() != null ? s.getReps() : 0,
                                        formatWeight(s.getWeight())
                                ));
                            }
                        }
                        workoutsList.append("\n");
                    }
                } catch (Exception e) {
                    workoutsList.append("_(Erro ao ler exercícios)_\n\n");
                }
            }
        }

        // — Totais vs. metas —
        UserTelegram user = report.getUser();
        int targetCal  = (user.getTargetCalories() != null && user.getTargetCalories() > 0) ? user.getTargetCalories() : 2000;
        int targetProt = (user.getTargetProtein()  != null && user.getTargetProtein()  > 0) ? user.getTargetProtein()  : 150;
        int targetCarb = (user.getTargetCarbs()    != null && user.getTargetCarbs()    > 0) ? user.getTargetCarbs()    : 200;
        int targetFat  = (user.getTargetFat()      != null && user.getTargetFat()      > 0) ? user.getTargetFat()      : 60;

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
                report.getTotalCalories(), targetCal,
                formatDouble(report.getTotalProtein()), targetProt,
                formatDouble(report.getTotalCarbs()), targetCarb,
                formatDouble(report.getTotalFat()), targetFat
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
