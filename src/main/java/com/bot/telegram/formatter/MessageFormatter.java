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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formata todas as mensagens enviadas pelo ShapeLog.ai seguindo a identidade
 * editorial (Opção A): cabeçalhos em Title Case, blockquotes nativos do
 * Telegram, barra monoespaçada e proteção contra o limite de 4096 caracteres.
 */
@Component
public class MessageFormatter {

    static final int MAX_REPORT_LENGTH = 3900;
    private static final int REPORT_FOOTER_RESERVE = 110;
    private static final int DEFAULT_CALORIES = 2000;
    private static final int DEFAULT_PROTEIN = 150;
    private static final int DEFAULT_CARBS = 200;
    private static final int DEFAULT_FAT = 60;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DecimalFormatSymbols PT_BR_SYMBOLS =
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"));

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
        String name = (firstName != null && !firstName.isBlank()) ? escapeMarkdown(firstName.trim()) : "Usuário";
        String examples = toBlockquote(
                "*Exemplos para testar agora:*\n" +
                "• _\"3 ovos mexidos com pão integral\"_\n" +
                "• _\"Supino reto 4x10 60kg\"_"
        );
        return "👋 Olá, *" + name + "*\\! Bem\\-vindo ao *ShapeLog\\.ai*\n\n" +
                "Envie um texto ou áudio direto aqui — eu identifico sua refeição ou treino automaticamente\\.\n\n" +
                examples + "\n\n" +
                "Atalhos rápidos:\n" +
                "/relatorio · /meta · /treino · /refeicao · /exercicio";
    }

    public String formatPendingInputPrompt(String text, boolean isVoice) {
        if (isVoice) {
            return "🎙️ *Áudio recebido*\n\nComo deseja registrar?";
        }
        String safeText = text != null ? text.trim() : "";
        String preview = safeText.length() > 120 ? safeText.substring(0, 120) + "…" : safeText;
        return "📥 *Mensagem recebida*\n\n" +
                toBlockquote("_\"" + escapeMarkdown(preview) + "\"_") + "\n\n" +
                "Como deseja registrar?";
    }

    public String formatGoalsUpdated(int cal, int prot, int carb, int fat) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 *Metas diárias atualizadas*\n\n")
          .append(formatMacroTargetsLines(cal, prot, carb, fat));

        int macroKcal = prot * 4 + carb * 4 + fat * 9;
        if (cal > 0 && Math.abs(cal - macroKcal) > cal * 0.15) {
            sb.append("\n\n").append(toBlockquote(
                    "ℹ️ *Atenção:* a soma dos seus macros dá \\~" + code(formatInt(macroKcal) + " kcal") +
                    " \\(4 kcal/g P e C, 9 kcal/g G\\)\\. Se quiser ajustar, envie /meta\\."
            ));
        }
        return sb.toString();
    }

    public String formatCurrentGoals(UserTelegram user) {
        int[] t = resolveTargets(user);
        String howToUpdate = toBlockquote(
                "*Como atualizar:*\n" +
                "• Envie os 4 números \\(ex: " + code("2000 150 200 60") + "\\)\n" +
                "• Ou descreva seu objetivo por texto ou áudio \\(ex: _\"tenho 80kg, quero secar\"_\\)"
        );
        return "🎯 *Suas metas atuais*\n\n" +
                formatMacroTargetsLines(t[0], t[1], t[2], t[3]) + "\n\n" +
                howToUpdate;
    }

    public String formatGoalSuggestion(GoalDto dto) {
        int cal = dto.getCalories() != null ? dto.getCalories() : 0;
        int prot = dto.getProtein() != null ? dto.getProtein() : 0;
        int carb = dto.getCarbs() != null ? dto.getCarbs() : 0;
        int fat = dto.getFat() != null ? dto.getFat() : 0;

        StringBuilder quoteBody = new StringBuilder();
        if (dto.getExplanation() != null && !dto.getExplanation().isBlank()) {
            quoteBody.append("💡 ").append(escapeMarkdown(dto.getExplanation().trim())).append("\n\n");
        }
        quoteBody.append("_Estimativa por IA — consulte um nutricionista para prescrição individualizada\\._");

        return "🎯 *Sugestão de metas diárias*\n\n" +
                formatMacroTargetsLines(cal, prot, carb, fat) + "\n\n" +
                toBlockquote(quoteBody.toString());
    }

    public String formatMealRegistered(Meal meal) {
        return buildMealCardText(meal, false);
    }

    public String formatMealRegistered(Meal meal, DailyReportDto dailyReport, LocalDate date) {
        return formatMealRegistered(meal, dailyReport, date, false);
    }

    public String formatMealRegistered(Meal meal, DailyReportDto dailyReport, LocalDate date, boolean edited) {
        String base = buildMealCardText(meal, edited);
        if (dailyReport == null) {
            return base;
        }
        int[] t = resolveTargets(dailyReport.getUser());
        int totalCal = dailyReport.getTotalCalories() != null ? dailyReport.getTotalCalories() : 0;
        double totalProt = dailyReport.getTotalProtein() != null ? dailyReport.getTotalProtein() : 0.0;

        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        String dateLabel = escapeMarkdown(formatDateLabel(safeDate));
        String bar = buildProgressBar(totalCal, t[0]);
        String calDelta = escapeMarkdown(formatCaloriesDelta(totalCal, t[0], false));
        String protStatus = formatProteinStatus(totalProt, t[1]);

        String progressQuote = toBlockquote(
                "🎯 *Progresso · " + dateLabel + "*\n" +
                bar + "\n" +
                "🔥 " + code(formatInt(totalCal) + " / " + formatInt(t[0]) + " kcal") + " · " + calDelta + "\n" +
                "💪 Proteína " + code(formatNumber(totalProt) + " / " + formatInt(t[1]) + "g") + " · " + protStatus
        );
        return base + "\n\n" + progressQuote;
    }

    public String formatExercisesAdded(List<WorkoutDto.ExerciseDto> exercises) {
        return formatExercisesAdded(exercises, null, false);
    }

    public String formatExercisesAdded(List<WorkoutDto.ExerciseDto> exercises, String autoCreatedWorkoutTitle) {
        return formatExercisesAdded(exercises, autoCreatedWorkoutTitle, false);
    }

    public String formatExercisesAdded(List<WorkoutDto.ExerciseDto> exercises, String autoCreatedWorkoutTitle, boolean edited) {
        if (exercises == null || exercises.isEmpty()) {
            return "⚠️ Nenhum exercício reconhecido\\.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Exercício registrado*");
        if (edited) {
            sb.append(" _\\(editado\\)_");
        }
        sb.append("\n\n");

        if (autoCreatedWorkoutTitle != null && !autoCreatedWorkoutTitle.isBlank()) {
            sb.append("🏋️ *Treino:* ")
              .append(escapeMarkdown(autoCreatedWorkoutTitle.trim()))
              .append(" _\\(criado automaticamente\\)_\n\n");
        }

        for (int i = 0; i < exercises.size(); i++) {
            String block = buildDetailedExerciseBlock(exercises.get(i));
            if (!block.isEmpty()) {
                if (i > 0) {
                    sb.append("\n\n");
                }
                sb.append(toBlockquote(block));
            }
        }
        return sb.toString().trim();
    }

    public String formatWorkoutConflictPrompt(String existingTitle, String newTitle) {
        String existingSafe = (existingTitle != null && !existingTitle.isBlank()) ? existingTitle.trim() : "Treino";
        String newSafe = (newTitle != null && !newTitle.isBlank()) ? newTitle.trim() : "Treino";
        return "🏋️ *Treino já iniciado hoje*\n\n" +
                toBlockquote(
                        "Você já tem o treino *" + escapeMarkdown(existingSafe) +
                        "* hoje com exercícios registrados\\.\n" +
                        "O que deseja fazer com *" + escapeMarkdown(newSafe) + "*?"
                );
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

        int[] t = resolveTargets(user);
        int totalCal = (report != null && report.getTotalCalories() != null) ? report.getTotalCalories() : 0;
        double totalProt = (report != null && report.getTotalProtein() != null) ? report.getTotalProtein() : 0.0;
        double totalCarb = (report != null && report.getTotalCarbs() != null) ? report.getTotalCarbs() : 0.0;
        double totalFat = (report != null && report.getTotalFat() != null) ? report.getTotalFat() : 0.0;

        String dateLabel = escapeMarkdown(formatDateLabel(safeDate));
        String calBar = buildProgressBar(totalCal, t[0]);
        String calDelta = escapeMarkdown(formatCaloriesDelta(totalCal, t[0], true));
        int protPct = calculatePercentage(totalProt, t[1]);
        int carbPct = calculatePercentage(totalCarb, t[2]);
        int fatPct = calculatePercentage(totalFat, t[3]);
        String protCheck = totalProt >= t[1] ? " ✅" : "";

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Relatório · ").append(dateLabel).append("*\n\n")
          .append(calBar).append(" das calorias\n\n")
          .append("🔥 *Calorias:* ").append(code(formatInt(totalCal) + " / " + formatInt(t[0]))).append("\n")
          .append("   ").append(calDelta).append("\n")
          .append("💪 *Proteína:* ").append(code(formatNumber(totalProt) + " / " + formatInt(t[1]) + "g"))
          .append(" · ").append(protPct).append("%").append(protCheck).append("\n")
          .append("🍞 *Carbos:* ").append(code(formatNumber(totalCarb) + " / " + formatInt(t[2]) + "g"))
          .append(" · ").append(carbPct).append("%\n")
          .append("🥑 *Gorduras:* ").append(code(formatNumber(totalFat) + " / " + formatInt(t[3]) + "g"))
          .append(" · ").append(fatPct).append("%\n\n");

        // Reserva orçamento proporcional quando há treinos para evitar que muitas refeições escondam os treinos
        int remainingAfterHeader = MAX_REPORT_LENGTH - sb.length();
        int workoutsReserve = workouts.isEmpty() ? 90 : Math.min(1200, Math.max(400, remainingAfterHeader / 2));
        int mealsLimit = sb.length() + Math.max(200, remainingAfterHeader - workoutsReserve);

        appendMealsSection(sb, meals, emptySuffix, mealsLimit);
        sb.append("\n\n");
        appendWorkoutsSection(sb, workouts, emptySuffix, MAX_REPORT_LENGTH);

        return sb.toString().trim();
    }

    // --- Mensagens de estado, sistema, erro e administração ---

    public String formatLoading(String action) {
        return "⏳ " + action + "...";
    }

    public String formatAwaitingMealPrompt() {
        return "🎙️ *Aguardando refeição*\n\nEnvie um áudio ou descreva os alimentos em texto agora\\.";
    }

    public String formatAwaitingWorkoutTitlePrompt() {
        return "🏋️ *Novo treino*\n\nQual o título do treino de hoje? \\(Ex: _Costas e Bíceps_, _Treino A_\\)";
    }

    public String formatAwaitingExercisePrompt() {
        return "🎙️ *Aguardando exercício*\n\nEnvie um áudio ou descreva o exercício com séries e carga agora\\.";
    }

    public String formatEditMealPrompt() {
        return "✏️ *Editando refeição*\n\n" +
                toBlockquote("Diga o que corrigir \\(ex: _\"foram 150g de arroz\"_\\) ou descreva a refeição novamente\\.");
    }

    public String formatEditExercisePrompt() {
        return "✏️ *Editando exercício*\n\n" +
                toBlockquote("Envie um texto ou áudio com os novos dados deste exercício\\.");
    }

    public String formatWorkoutStarted(String title, boolean isNew, boolean renamed) {
        String safeTitle = (title != null && !title.isBlank()) ? escapeMarkdown(title.trim()) : "Treino";
        if (renamed) {
            return "✏️ *Título atualizado* para *" + safeTitle + "*\n\nEnvie seus exercícios em texto ou áudio agora\\.";
        }
        String header = isNew ? "🏋️ *Novo treino iniciado:*" : "🏋️ *Treino iniciado:*";
        return header + " *" + safeTitle + "*\n\nEnvie seus exercícios em texto ou áudio agora \\(ex: _Supino reto 3x12 50kg_\\)\\.";
    }

    public String formatItemUpdated(String itemName, boolean feminine) {
        String suffix = feminine ? "atualizada" : "atualizado";
        return "✅ *" + escapeMarkdown(itemName) + " " + suffix + "*";
    }

    public String formatItemDeleted(String itemName, boolean feminine) {
        String suffix = feminine ? "excluída" : "excluído";
        return "🗑️ *" + escapeMarkdown(itemName) + " " + suffix + "*";
    }

    public String formatAlreadyRemoved(String subject, boolean feminine) {
        String suffix = feminine ? "já foi removida" : "já foi removido";
        return "⚠️ " + escapeMarkdown(subject) + " " + suffix + "\\.";
    }

    public String formatActionCancelled() {
        return "✖️ *Operação cancelada*";
    }

    public String formatInputExpired(String retryHint) {
        return "⚠️ *Solicitação expirada*\n\n" + escapeMarkdown(retryHint);
    }

    public String formatUnknownCommand() {
        return "⚠️ *Comando não reconhecido*\n\nUse /relatorio, /refeicao, /treino, /exercicio ou /meta — ou envie texto/áudio direto\\.";
    }

    public String formatUnknownState() {
        return "⚠️ Contexto não reconhecido\\. Envie /start ou descreva o que deseja registrar\\.";
    }

    public String formatVoiceWorkoutTitleRequired() {
        return "⚠️ Digite primeiro o *título do treino* em texto \\(ex: _Peito e Tríceps_\\)\\.";
    }

    public String formatInvalidGoalMissingInput() {
        return "⚠️ Informe os 4 valores ou descreva seu objetivo por texto ou áudio\\.";
    }

    public String formatInvalidGoalFormat() {
        return "⚠️ *Formato incorreto*\n\n" +
                toBlockquote(
                        "Use: " + code("/meta calorias proteínas carbos gorduras") + "\n" +
                        "💡 Ex: " + code("/meta 2000 150 200 60") + "\n" +
                        "Ou descreva em texto \\(ex: _\"peso 80kg quero secar\"_\\)\\."
                );
    }

    public String formatInvalidGoalValues() {
        return "⚠️ Os valores das metas devem ser números inteiros maiores que zero\\.\n\n💡 Ex: " +
                code("/meta 2000 150 200 60");
    }

    public String formatInvalidDateFormat() {
        return "⚠️ *Formato de data inválido*\n\nUse /relatorio, /relatorio ontem ou /relatorio DD/MM/AAAA\\.";
    }

    public String formatVoiceTooLarge() {
        return "⚠️ *Áudio muito longo ou grande*\n\nEnvie um áudio de até 2 minutos \\(2 MB\\) ou descreva em texto\\.";
    }

    public String formatVoiceDownloadError() {
        return "⚠️ Não foi possível baixar o áudio\\. Tente reenviar ou descreva em texto\\.";
    }

    public String formatUnexpectedError() {
        return "⚠️ Ocorreu um erro inesperado\\. Tente novamente em instantes\\.";
    }

    public String formatWarning(String plainMessage) {
        String safe = (plainMessage != null && !plainMessage.isBlank())
                ? plainMessage.trim()
                : "Não foi possível concluir a solicitação.";
        boolean hasLeadingIcon = safe.startsWith("⚠️") || safe.startsWith("⏳")
                || safe.startsWith("⛔") || safe.startsWith("❌");
        String prefix = hasLeadingIcon ? "" : "⚠️ ";
        return prefix + escapeMarkdown(safe);
    }

    public String formatAccessDenied() {
        return "⛔ *Acesso restrito*\n\nSolicite permissão ao administrador para utilizar o *ShapeLog\\.ai*\\.";
    }

    public String formatAccessRequestForAdmin(String firstName, String username, long chatId) {
        String safeName = (firstName != null && !firstName.isBlank()) ? escapeMarkdown(firstName.trim()) : "sem nome";
        String safeUsername = (username != null && !username.isBlank()) ? "@" + escapeMarkdown(username.trim()) : "sem username";
        return "🔔 *Solicitação de acesso*\n\n" +
                toBlockquote(
                        "👤 *Nome:* " + safeName + "\n" +
                        "🏷️ *Username:* " + safeUsername + "\n" +
                        "🆔 *ID:* " + code(String.valueOf(chatId))
                ) + "\n\n" +
                "Para aprovar, envie:\n" + code("/aprovar " + chatId);
    }

    public String formatAdminUsageError(String usageCommand) {
        return "⚠️ *Uso incorreto*\n\nUse: " + code(usageCommand);
    }

    public String formatInvalidNumericId() {
        return "⚠️ ID inválido\\! O ID deve ser numérico\\.";
    }

    public String formatUserNotFound() {
        return "⚠️ Usuário não encontrado com o ID informado\\.";
    }

    public String formatUserApprovedForAdmin(String firstName, long id) {
        String safeName = (firstName != null && !firstName.isBlank()) ? escapeMarkdown(firstName.trim()) : "Usuário";
        return "✅ *Usuário aprovado*\n\n👤 *Nome:* " + safeName + "\n🆔 *ID:* " + code(String.valueOf(id));
    }

    public String formatUserApprovedForUser() {
        return "🎉 *Acesso aprovado*\n\nSeu acesso ao *ShapeLog\\.ai* foi liberado\\! Envie /start para começar\\.";
    }

    public String formatUserRevokedForAdmin(String firstName, long id) {
        String safeName = (firstName != null && !firstName.isBlank()) ? escapeMarkdown(firstName.trim()) : "Usuário";
        return "🚫 *Acesso revogado*\n\n👤 *Nome:* " + safeName + "\n🆔 *ID:* " + code(String.valueOf(id));
    }

    public String formatApprovedUsersList(List<UserTelegram> approvedUsers) {
        if (approvedUsers == null || approvedUsers.isEmpty()) {
            return "📋 *Usuários aprovados*\n\nNenhum usuário aprovado no momento\\.";
        }
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < approvedUsers.size(); i++) {
            UserTelegram u = approvedUsers.get(i);
            String name = (u.getFirstName() != null && !u.getFirstName().isBlank())
                    ? escapeMarkdown(u.getFirstName().trim())
                    : "Sem nome";
            String uname = (u.getUsername() != null && !u.getUsername().isBlank())
                    ? " @" + escapeMarkdown(u.getUsername().trim())
                    : "";
            if (i > 0) {
                lines.append("\n");
            }
            lines.append("• ").append(name).append(uname)
                 .append(" \\(ID: ").append(code(String.valueOf(u.getId()))).append("\\)");
        }
        return "📋 *Usuários aprovados \\(" + approvedUsers.size() + "\\)*\n\n" + toBlockquote(lines.toString());
    }

    // --- Helpers de layout e formatação ---

    String toBlockquote(String escapedMultiline) {
        if (escapedMultiline == null || escapedMultiline.isEmpty()) {
            return ">";
        }
        String[] lines = escapedMultiline.split("\n", -1);
        StringBuilder sb = new StringBuilder(escapedMultiline.length() + lines.length * 2);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines[i].isEmpty() ? ">" : "> " + lines[i]);
        }
        return sb.toString();
    }

    String formatSeriesCompact(List<WorkoutDto.SeriesDto> series) {
        if (series == null || series.isEmpty()) {
            return "";
        }
        boolean uniformReps = true;
        boolean uniformWeight = true;
        boolean allHaveWeight = true;
        int firstReps = normalizedReps(series.get(0));
        double firstWeight = normalizedWeight(series.get(0));

        for (WorkoutDto.SeriesDto s : series) {
            int reps = normalizedReps(s);
            double weight = normalizedWeight(s);
            if (reps != firstReps) {
                uniformReps = false;
            }
            if (Math.abs(weight - firstWeight) >= 0.01) {
                uniformWeight = false;
            }
            if (weight <= 0) {
                allHaveWeight = false;
            }
        }

        if (uniformReps && uniformWeight) {
            String base = series.size() + "×" + firstReps;
            return firstWeight > 0 ? base + " · " + formatNumber(firstWeight) + " kg" : base;
        }

        if (uniformWeight) {
            StringBuilder repsPart = new StringBuilder();
            for (int i = 0; i < series.size(); i++) {
                if (i > 0) repsPart.append("/");
                repsPart.append(normalizedReps(series.get(i)));
            }
            repsPart.append(" reps");
            return firstWeight > 0
                    ? repsPart + " · " + formatNumber(firstWeight) + " kg"
                    : repsPart.toString();
        }

        StringBuilder setsPart = new StringBuilder();
        for (int i = 0; i < series.size(); i++) {
            if (i > 0) setsPart.append(" · ");
            WorkoutDto.SeriesDto s = series.get(i);
            int reps = normalizedReps(s);
            double weight = normalizedWeight(s);
            if (allHaveWeight) {
                setsPart.append(reps).append("×").append(formatNumber(weight));
            } else if (weight > 0) {
                setsPart.append(reps).append("×").append(formatNumber(weight)).append("kg");
            } else {
                setsPart.append(reps).append(" reps");
            }
        }
        if (allHaveWeight) {
            setsPart.append(" kg");
        }
        return setsPart.toString();
    }

    private String buildMealCardText(Meal meal, boolean edited) {
        String header = "✅ *Refeição registrada*" + (edited ? " _\\(editada\\)_" : "");
        String desc = (meal.getDescription() != null && !meal.getDescription().isBlank())
                ? escapeMarkdown(meal.getDescription().trim())
                : "sem descrição";
        int cal = meal.getCalories() != null ? meal.getCalories() : 0;

        return header + "\n\n" +
                "🥗 " + desc + "\n" +
                code(formatInt(cal) + " kcal") +
                " · P " + escapeMarkdown(formatNumber(meal.getProtein())) + "g" +
                " · C " + escapeMarkdown(formatNumber(meal.getCarbs())) + "g" +
                " · G " + escapeMarkdown(formatNumber(meal.getFat())) + "g";
    }

    private String formatMacroTargetsLines(int cal, int prot, int carb, int fat) {
        return "🔥 *Calorias:* " + code(formatInt(cal) + " kcal") + "\n" +
               "💪 *Proteínas:* " + code(formatInt(prot) + "g") + "\n" +
               "🍞 *Carbos:* " + code(formatInt(carb) + "g") + "\n" +
               "🥑 *Gorduras:* " + code(formatInt(fat) + "g");
    }

    private void appendMealsSection(StringBuilder sb, List<Meal> meals, String emptySuffix, int mealsLimit) {
        if (meals.isEmpty()) {
            sb.append("🥗 *Refeições*\n")
              .append("Nenhuma refeição registrada ").append(emptySuffix);
            return;
        }
        sb.append("🥗 *Refeições \\(").append(meals.size()).append("\\)*\n");
        int shown = 0;
        for (int i = 0; i < meals.size(); i++) {
            Meal meal = meals.get(i);
            String desc = (meal.getDescription() != null && !meal.getDescription().isBlank())
                    ? escapeMarkdown(meal.getDescription().trim())
                    : "sem descrição";
            int cal = meal.getCalories() != null ? meal.getCalories() : 0;
            String macroLine = code(formatInt(cal) + " kcal") +
                    " · P " + escapeMarkdown(formatNumber(meal.getProtein())) + "g" +
                    " · C " + escapeMarkdown(formatNumber(meal.getCarbs())) + "g" +
                    " · G " + escapeMarkdown(formatNumber(meal.getFat())) + "g";
            String entry = toBlockquote((i + 1) + "\\. " + desc + "\n" + macroLine);
            String sep = shown > 0 ? "\n\n" : "";

            if (sb.length() + sep.length() + entry.length() > mealsLimit - REPORT_FOOTER_RESERVE) {
                int omitted = meals.size() - shown;
                if (shown > 0) sb.append("\n\n");
                sb.append(toBlockquote("… \\+" + omitted + " refeição\\(ões\\) omitida\\(s\\) pelo limite do Telegram"));
                return;
            }
            sb.append(sep).append(entry);
            shown++;
        }
    }

    private void appendWorkoutsSection(StringBuilder sb, List<WorkoutSession> workouts, String emptySuffix, int maxLen) {
        if (workouts.isEmpty()) {
            sb.append("🏋️ *Treinos*\n")
              .append("Nenhum treino registrado ").append(emptySuffix);
            return;
        }
        sb.append("🏋️ *Treinos \\(").append(workouts.size()).append("\\)*\n");

        for (int wIdx = 0; wIdx < workouts.size(); wIdx++) {
            WorkoutSession workout = workouts.get(wIdx);
            List<String> lines = buildWorkoutReportLines(workout);
            String blockSep = wIdx > 0 ? "\n\n" : "";

            // Verifica se cabe ao menos o cabeçalho da sessão + 1 linha
            String firstLine = lines.get(0);
            String quotedFirst = toBlockquote(firstLine);
            if (sb.length() + blockSep.length() + quotedFirst.length() > maxLen - REPORT_FOOTER_RESERVE) {
                int remainingWorkouts = workouts.size() - wIdx;
                if (wIdx > 0) sb.append("\n\n");
                sb.append(toBlockquote("… \\+" + remainingWorkouts + " treino\\(s\\) omitido\\(s\\) pelo limite do Telegram"));
                return;
            }

            sb.append(blockSep).append(quotedFirst);

            for (int lIdx = 1; lIdx < lines.size(); lIdx++) {
                String quotedLine = "\n" + toBlockquote(lines.get(lIdx));
                if (sb.length() + quotedLine.length() > maxLen - REPORT_FOOTER_RESERVE) {
                    int remainingWorkouts = workouts.size() - wIdx - 1;
                    String notice = remainingWorkouts > 0
                            ? "… treino truncado e \\+" + remainingWorkouts + " treino\\(s\\) omitido\\(s\\) pelo limite do Telegram"
                            : "… restante do treino omitido pelo limite do Telegram";
                    sb.append("\n").append(toBlockquote(notice));
                    return;
                }
                sb.append(quotedLine);
            }
        }
    }

    private List<String> buildWorkoutReportLines(WorkoutSession workout) {
        List<String> lines = new ArrayList<>();
        String titulo = workout.getRawInput();
        if (titulo == null || titulo.isBlank() || titulo.startsWith("[")) {
            titulo = "Treino";
        }
        StringBuilder titleLine = new StringBuilder("*").append(escapeMarkdown(titulo.trim())).append("*");
        if (workout.getDurationMinutes() != null && workout.getDurationMinutes() > 0) {
            titleLine.append(" · ").append(workout.getDurationMinutes()).append(" min");
        }
        lines.add(titleLine.toString());

        String json = workout.getExercisesJson();
        if (json == null || json.isBlank() || json.equals("[]")) {
            lines.add("_\\(Nenhum exercício registrado ainda\\)_");
            return lines;
        }
        try {
            List<WorkoutDto.ExerciseDto> exs = workoutService.deserializeExercises(json);
            if (exs == null || exs.isEmpty()) {
                lines.add("_\\(Nenhum exercício registrado ainda\\)_");
                return lines;
            }
            for (WorkoutDto.ExerciseDto ex : exs) {
                if (ex == null) continue;
                String name = (ex.getName() != null && !ex.getName().isBlank()) ? ex.getName().trim() : "Exercício";
                String compact = formatSeriesCompact(ex.getSeries());
                StringBuilder exLine = new StringBuilder("• *").append(escapeMarkdown(name)).append("*");
                if (!compact.isEmpty()) {
                    exLine.append(" — ").append(escapeMarkdown(compact));
                }
                lines.add(exLine.toString());

                if (ex.getDetails() != null) {
                    for (String detail : ex.getDetails()) {
                        if (detail != null && !detail.isBlank()) {
                            lines.add("  ↳ " + escapeMarkdown(detail.trim()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            lines.add("_\\(Erro ao ler exercícios\\)_");
        }
        return lines;
    }

    private String buildDetailedExerciseBlock(WorkoutDto.ExerciseDto ex) {
        if (ex == null) return "";
        String name = (ex.getName() != null && !ex.getName().isBlank()) ? ex.getName().trim() : "Exercício";
        StringBuilder sb = new StringBuilder();
        sb.append("🔹 *").append(escapeMarkdown(name)).append("*");

        List<WorkoutDto.SeriesDto> series = ex.getSeries();
        if (series != null && !series.isEmpty()) {
            for (int i = 0; i < series.size(); i++) {
                WorkoutDto.SeriesDto s = series.get(i);
                int reps = normalizedReps(s);
                double weight = normalizedWeight(s);
                sb.append("\n• ").append(i + 1).append("ª · ").append(reps).append(" reps");
                if (weight > 0) {
                    sb.append(" · ").append(escapeMarkdown(formatNumber(weight))).append(" kg");
                }
            }
        }

        List<String> details = ex.getDetails();
        if (details != null && !details.isEmpty()) {
            for (String detail : details) {
                if (detail != null && !detail.isBlank()) {
                    sb.append("\n• ").append(escapeMarkdown(detail.trim()));
                }
            }
        }
        return sb.toString();
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
        return code("█".repeat(filled) + "░".repeat(10 - filled)) + " " + pct + "%";
    }

    private int calculatePercentage(double current, int target) {
        if (target <= 0) return 0;
        return (int) Math.round((current * 100.0) / target);
    }

    private String formatDateLabel(LocalDate date) {
        LocalDate today = LocalDate.now(appZoneId);
        if (date.equals(today)) {
            return "Hoje (" + date.format(SHORT_DATE_FORMATTER) + ")";
        }
        if (date.equals(today.minusDays(1))) {
            return "Ontem (" + date.format(SHORT_DATE_FORMATTER) + ")";
        }
        return date.format(DATE_FORMATTER);
    }

    private String formatCaloriesDelta(int consumed, int target, boolean includeUnit) {
        String unit = includeUnit ? " kcal" : "";
        if (consumed <= target) {
            return "faltam " + formatInt(target - consumed) + unit;
        }
        return "excedeu " + formatInt(consumed - target) + unit;
    }

    private String formatProteinStatus(double consumed, int target) {
        if (consumed >= target) {
            return "meta batida\\! ✅";
        }
        return escapeMarkdown("faltam " + formatNumber(target - consumed) + "g");
    }

    private int normalizedReps(WorkoutDto.SeriesDto s) {
        return (s != null && s.getReps() != null && s.getReps() > 0) ? s.getReps() : 0;
    }

    private double normalizedWeight(WorkoutDto.SeriesDto s) {
        return (s != null && s.getWeight() != null && s.getWeight() > 0) ? s.getWeight() : 0.0;
    }

    private String code(String raw) {
        if (raw == null) return "``";
        return "`" + raw.replace("\\", "\\\\").replace("`", "\\`") + "`";
    }

    private String formatInt(int value) {
        return new DecimalFormat("#,##0", PT_BR_SYMBOLS).format(value);
    }

    private String formatNumber(Double value) {
        if (value == null) return "0";
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05) {
            return formatInt((int) rounded);
        }
        return String.format(Locale.US, "%.1f", value).replace('.', ',');
    }
}
