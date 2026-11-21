package com.bot.telegram.formatter;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.IWorkoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class MessageFormatter {

    private static final int DEFAULT_CALORIES = 2000;
    private static final int DEFAULT_PROTEIN = 150;
    private static final int DEFAULT_CARBS = 200;
    private static final int DEFAULT_FAT = 60;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final IWorkoutService workoutService;
    private final ZoneId appZoneId;

    public MessageFormatter(IWorkoutService workoutService) {
        this(workoutService, DEFAULT_ZONE);
    }

    @Autowired
    public MessageFormatter(IWorkoutService workoutService, ZoneId appZoneId) {
        this.workoutService = workoutService;
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    public String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("`", "\\`")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("~", "\\~")
                   .replace(">", "\\>")
                   .replace("#", "\\#")
                   .replace("+", "\\+")
                   .replace("-", "\\-")
                   .replace("=", "\\=")
                   .replace("|", "\\|")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace(".", "\\.")
                   .replace("!", "\\!");
    }

    public String formatStart(String firstName) {
        String name = firstName != null ? escapeMarkdown(firstName) : "Usuário";
        return "👋 Olá, *" + name + "*\\! Bem\\-vindo ao *ShapeLog\\.ai*\\.\n\n" +
                "Aqui você pode registrar sua alimentação e treinos diários por texto ou áudio\\!\n\n" +
                "📌 *Comandos Disponíveis:*\n" +
                "🥗 `/refeicao <alimentos>` \\- Registra uma refeição\n" +
                "🏋️‍♂️ `/treino <titulo>` \\- Registra um treino\n" +
                "💪 `/exercicio <exercicios>` \\- Adiciona exercícios ao treino do dia\n" +
                "🎯 `/meta <calorias> <proteinas> <carbos> <gorduras>` \\- Define metas\n" +
                "📊 `/relatorio` \\- Exibe o progresso diário";
    }

    public String formatPendingInputPrompt(String text, boolean isVoice) {
        if (isVoice) {
            return "🎙️ *Áudio recebido\\!*\n\nComo deseja registrar?";
        }
        String safeText = text != null ? text.trim() : "";
        String preview = safeText.length() > 120 ? safeText.substring(0, 120) + "..." : safeText;
        return "📥 *Mensagem recebida:*\n_\"" + escapeMarkdown(preview) + "\"_\n\nComo deseja registrar?";
    }

    public String formatGoalsUpdated(int cal, int prot, int carb, int fat) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "🎯 *Metas Diárias Atualizadas\\!*\n" +
                "🔥 *Calorias:* %d kcal\n" +
                "💪 *Proteínas:* %dg\n" +
                "🍞 *Carboidratos:* %dg\n" +
                "🥑 *Gorduras:* %dg",
                cal, prot, carb, fat
        ));
        int macroKcal = prot * 4 + carb * 4 + fat * 9;
        if (cal > 0 && Math.abs(cal - macroKcal) > cal * 0.15) {
            sb.append(String.format(
                    "\n\nℹ️ _Atenção: a soma dos seus macros dá \\~%d kcal \\(4 kcal/g P e C, 9 kcal/g G\\)\\. Se quiser ajustar, envie /meta\\._",
                    macroKcal
            ));
        }
        return sb.toString();
    }

    public String formatCurrentGoals(UserTelegram user) {
        int[] t = resolveTargets(user);
        return String.format(
                "🎯 *Suas Metas Atuais*\n\n" +
                "🔥 *Calorias:* %d kcal\n" +
                "💪 *Proteínas:* %dg\n" +
                "🍞 *Carboidratos:* %dg\n" +
                "🥑 *Gorduras:* %dg\n\n" +
                "✏️ *Como atualizar:*\n" +
                "• Envie os 4 valores \\(ex: `2000 150 200 60`\\)\n" +
                "• Ou descreva seu objetivo por texto ou áudio \\(ex: _\"tenho 80kg, quero secar\"_\\)",
                t[0], t[1], t[2], t[3]
        );
    }

    public String formatGoalSuggestion(GoalDto dto) {
        int cal = dto.getCalories() != null ? dto.getCalories() : 0;
        int prot = dto.getProtein() != null ? dto.getProtein() : 0;
        int carb = dto.getCarbs() != null ? dto.getCarbs() : 0;
        int fat = dto.getFat() != null ? dto.getFat() : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 *Sugestão de Metas Diárias*\n\n");
        sb.append(String.format("🔥 *Calorias:* `%d kcal`\n", cal));
        sb.append(String.format("💪 *Proteínas:* `%dg`\n", prot));
        sb.append(String.format("🍞 *Carboidratos:* `%dg`\n", carb));
        sb.append(String.format("🥑 *Gorduras:* `%dg`", fat));
        if (dto.getExplanation() != null && !dto.getExplanation().isBlank()) {
            sb.append("\n\n💡 ").append(escapeMarkdown(dto.getExplanation().trim()));
        }
        sb.append("\n\nℹ️ _Estimativa informativa gerada por IA \\— consulte um nutricionista para prescrição individualizada\\._");
        return sb.toString();
    }

    public String formatMealRegistered(Meal meal) {
        return String.format(
                "✅ *Refeição Registrada\\!*\n\n" +
                "🥗 *Itens:* %s\n" +
                "📊 *Macros:* `%d kcal` \\| *P:* %sg \\| *C:* %sg \\| *G:* %sg",
                meal.getDescription() != null ? escapeMarkdown(meal.getDescription()) : "sem descrição",
                meal.getCalories() != null ? meal.getCalories() : 0,
                escapeMarkdown(formatDouble(meal.getProtein())),
                escapeMarkdown(formatDouble(meal.getCarbs())),
                escapeMarkdown(formatDouble(meal.getFat()))
        );
    }

    public String formatMealRegistered(Meal meal, DailyReportDto dailyReport, LocalDate date) {
        String base = formatMealRegistered(meal);
        if (dailyReport == null) {
            return base;
        }
        int[] t = resolveTargets(dailyReport.getUser());
        int totalCal = dailyReport.getTotalCalories() != null ? dailyReport.getTotalCalories() : 0;
        double totalProt = dailyReport.getTotalProtein() != null ? dailyReport.getTotalProtein() : 0.0;
        String label = date != null ? escapeMarkdown(formatDateHeader(date)) : "HOJE";
        String bar = buildProgressBar(totalCal, t[0]);
        String calDelta = formatCaloriesDelta(totalCal, t[0]);
        String protStatus = formatProteinStatus(totalProt, t[1]);

        return base + String.format(
                "\n\n🎯 *Progresso \\(%s\\):* %s\n" +
                "🔥 `%d / %d kcal` \\(%s\\)\n" +
                "💪 *Proteína:* `%s / %dg` \\(%s\\)",
                label,
                bar,
                totalCal, t[0], calDelta,
                formatDouble(totalProt), t[1], protStatus
        );
    }

    public String formatWorkoutRegistered(WorkoutSession session) {
        List<WorkoutDto.ExerciseDto> exercises = workoutService.deserializeExercises(session.getExercisesJson());
        StringBuilder sb = new StringBuilder();
        sb.append("🏋️\u200d♂️ *Treino Registrado*\n\n");
        String desc = session.getDescription() != null ? escapeMarkdown(session.getDescription()) : "Geral";
        sb.append("*Treino:* ").append(desc).append("\n\n");
        if (exercises != null) {
            for (WorkoutDto.ExerciseDto ex : exercises) {
                appendExerciseBlock(sb, ex);
            }
        }
        return sb.toString().trim();
    }

    public String formatExercisesAdded(List<WorkoutDto.ExerciseDto> exercises) {
        return formatExercisesAdded(exercises, null);
    }

    public String formatExercisesAdded(List<WorkoutDto.ExerciseDto> exercises, String autoCreatedWorkoutTitle) {
        if (exercises == null || exercises.isEmpty()) {
            return "Nenhum exercício reconhecido\\.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Exercício Registrado*\n\n");
        if (autoCreatedWorkoutTitle != null && !autoCreatedWorkoutTitle.isBlank()) {
            sb.append("🏋️‍♂️ *Treino:* ")
              .append(escapeMarkdown(autoCreatedWorkoutTitle))
              .append(" _\\(criado automaticamente\\)_\n\n");
        }
        for (WorkoutDto.ExerciseDto ex : exercises) {
            appendExerciseBlock(sb, ex);
        }
        return sb.toString().trim();
    }

    public String formatWorkoutConflictPrompt(String existingTitle, String newTitle) {
        String existingSafe = existingTitle != null && !existingTitle.isBlank() ? existingTitle : "Treino";
        String newSafe = newTitle != null && !newTitle.isBlank() ? newTitle : "Treino";
        return "🏋️‍♂️ Você já tem o treino *" + escapeMarkdown(existingSafe) +
               "* hoje com exercícios registrados\\.\n\nO que deseja fazer com *" +
               escapeMarkdown(newSafe) + "*?";
    }

    public String formatDailyReport(DailyReportDto report) {
        return formatDailyReport(report, LocalDate.now(appZoneId));
    }

    public String formatDailyReport(DailyReportDto report, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        LocalDate today = LocalDate.now(appZoneId);
        String emptySuffix = safeDate.equals(today) ? "hoje\\." : "neste dia\\.";

        List<Meal> meals = (report != null && report.getMeals() != null) ? report.getMeals() : List.of();
        List<WorkoutSession> workouts = (report != null && report.getWorkouts() != null) ? report.getWorkouts() : List.of();
        UserTelegram user = report != null ? report.getUser() : null;

        StringBuilder mealsList = new StringBuilder();
        int mealIdx = 1;
        for (Meal meal : meals) {
            mealsList.append(String.format("• *Refeição %d:* %s\n", mealIdx++, escapeMarkdown(meal.getDescription())));
            mealsList.append(String.format("  └ `%d kcal` \\| *P:* %sg \\| *C:* %sg \\| *G:* %sg\n\n",
                    meal.getCalories() != null ? meal.getCalories() : 0,
                    escapeMarkdown(formatDouble(meal.getProtein())),
                    escapeMarkdown(formatDouble(meal.getCarbs())),
                    escapeMarkdown(formatDouble(meal.getFat()))
            ));
        }
        if (meals.isEmpty()) {
            mealsList.append("Nenhuma refeição registrada ").append(emptySuffix).append("\n");
        }

        StringBuilder workoutsList = new StringBuilder();
        if (workouts.isEmpty()) {
            workoutsList.append("Nenhum treino registrado ").append(emptySuffix).append("\n");
        } else {
            for (WorkoutSession workout : workouts) {
                String titulo = workout.getRawInput();
                if (titulo == null || titulo.isBlank() || titulo.startsWith("[")) {
                    titulo = "Treino";
                }
                workoutsList.append("*Treino:* ").append(escapeMarkdown(titulo));
                if (workout.getDurationMinutes() != null && workout.getDurationMinutes() > 0) {
                    workoutsList.append(" \\(").append(workout.getDurationMinutes()).append(" min\\)");
                }
                workoutsList.append("\n\n");
                String json = workout.getExercisesJson();
                if (json == null || json.isBlank() || json.equals("[]")) {
                    workoutsList.append("_(Nenhum exercício registrado ainda)_\n\n");
                    continue;
                }
                try {
                    List<WorkoutDto.ExerciseDto> exs = workoutService.deserializeExercises(json);
                    for (WorkoutDto.ExerciseDto ex : exs) {
                        appendExerciseBlock(workoutsList, ex);
                    }
                } catch (Exception e) {
                    workoutsList.append("_(Erro ao ler exercícios)_\n\n");
                }
            }
        }

        int[] t = resolveTargets(user);
        int totalCal = (report != null && report.getTotalCalories() != null) ? report.getTotalCalories() : 0;
        double totalProt = (report != null && report.getTotalProtein() != null) ? report.getTotalProtein() : 0.0;
        double totalCarb = (report != null && report.getTotalCarbs() != null) ? report.getTotalCarbs() : 0.0;
        double totalFat = (report != null && report.getTotalFat() != null) ? report.getTotalFat() : 0.0;

        String dateHeader = formatDateHeader(safeDate);
        String calBar = buildProgressBar(totalCal, t[0]);
        String calDelta = formatCaloriesDelta(totalCal, t[0]);
        int protPct = calculatePercentage(totalProt, t[1]);
        String protStatus = formatProteinStatus(totalProt, t[1]);
        int carbPct = calculatePercentage(totalCarb, t[2]);
        int fatPct = calculatePercentage(totalFat, t[3]);

        return String.format(
                "📆 *RELATÓRIO DE %s*\n\n" +
                "📊 *TOTAIS VS\\. METAS*\n" +
                "%s\n" +
                "🔥 *Calorias:* `%d / %d kcal` \\(%s\\)\n" +
                "💪 *Proteínas:* `%s / %dg` \\(%d%% \\- %s\\)\n" +
                "🍞 *Carbos:* `%s / %dg` \\(%d%%\\)\n" +
                "🥑 *Gorduras:* `%s / %dg` \\(%d%%\\)\n\n" +
                "🥗 *REFEIÇÕES*\n%s\n\n" +
                "🏋️\u200d♂️ *TREINOS*\n%s",
                dateHeader,
                calBar,
                totalCal, t[0], calDelta,
                formatDouble(totalProt), t[1], protPct, protStatus,
                formatDouble(totalCarb), t[2], carbPct,
                formatDouble(totalFat), t[3], fatPct,
                mealsList.toString().trim(),
                workoutsList.toString().trim()
        );
    }

    private int[] resolveTargets(UserTelegram user) {
        if (user == null) {
            return new int[]{DEFAULT_CALORIES, DEFAULT_PROTEIN, DEFAULT_CARBS, DEFAULT_FAT};
        }
        int targetCal  = (user.getTargetCalories() != null && user.getTargetCalories() > 0) ? user.getTargetCalories() : DEFAULT_CALORIES;
        int targetProt = (user.getTargetProtein()  != null && user.getTargetProtein()  > 0) ? user.getTargetProtein()  : DEFAULT_PROTEIN;
        int targetCarb = (user.getTargetCarbs()    != null && user.getTargetCarbs()    > 0) ? user.getTargetCarbs()    : DEFAULT_CARBS;
        int targetFat  = (user.getTargetFat()      != null && user.getTargetFat()      > 0) ? user.getTargetFat()      : DEFAULT_FAT;
        return new int[]{targetCal, targetProt, targetCarb, targetFat};
    }

    private String buildProgressBar(double current, int target) {
        int pct = calculatePercentage(current, target);
        int filled = target > 0 ? (int) Math.round(Math.min(1.0, Math.max(0.0, current / target)) * 10) : 0;
        return "▓".repeat(filled) + "░".repeat(10 - filled) + " " + pct + "%";
    }

    private int calculatePercentage(double current, int target) {
        if (target <= 0) return 0;
        return (int) Math.round((current * 100.0) / target);
    }

    private String formatDateHeader(LocalDate date) {
        LocalDate today = LocalDate.now(appZoneId);
        if (date.equals(today)) {
            return "HOJE";
        }
        if (date.equals(today.minusDays(1))) {
            return "ONTEM";
        }
        return date.format(DATE_FORMATTER);
    }

    private String formatCaloriesDelta(int consumed, int target) {
        if (consumed <= target) {
            return escapeMarkdown("faltam " + (target - consumed) + " kcal");
        }
        return escapeMarkdown("excedeu " + (consumed - target) + " kcal");
    }

    private String formatProteinStatus(double consumed, int target) {
        if (consumed >= target) {
            return "meta batida\\! ✅";
        }
        return escapeMarkdown("faltam " + formatDouble(target - consumed) + "g");
    }

    private void appendExerciseBlock(StringBuilder sb, WorkoutDto.ExerciseDto ex) {
        if (ex == null) return;
        String name = (ex.getName() != null && !ex.getName().isBlank()) ? ex.getName() : "Exercício";
        sb.append("🔹 ").append(escapeMarkdown(name)).append(":\n");

        List<WorkoutDto.SeriesDto> series = ex.getSeries();
        if (series != null && !series.isEmpty()) {
            for (int i = 0; i < series.size(); i++) {
                WorkoutDto.SeriesDto s = series.get(i);
                sb.append(String.format("• %dª série: %d reps \\- %s kg\n",
                        (i + 1),
                        s.getReps() != null ? s.getReps() : 0,
                        escapeMarkdown(formatWeight(s.getWeight()))
                ));
            }
        }

        List<String> details = ex.getDetails();
        if (details != null && !details.isEmpty()) {
            for (String detail : details) {
                if (detail != null && !detail.isBlank()) {
                    sb.append("• ").append(escapeMarkdown(detail.trim())).append("\n");
                }
            }
        }

        sb.append("\n");
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
}
