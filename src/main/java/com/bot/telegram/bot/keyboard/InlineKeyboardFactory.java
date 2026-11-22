package com.bot.telegram.bot.keyboard;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Constrói os teclados inline do ShapeLog.ai respeitando limites de largura mobile
 * e confirmação em dois passos para exclusões.
 */
@Component
public class InlineKeyboardFactory {

    static final int MAX_DYNAMIC_LABEL_LENGTH = 22;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ZoneId appZoneId;

    public InlineKeyboardFactory() {
        this(DEFAULT_ZONE);
    }

    @Autowired
    public InlineKeyboardFactory(ZoneId appZoneId) {
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    public InlineKeyboardMarkup criarBotoesStart() {
        return markup(List.of(
                new InlineKeyboardRow(button("🎯 Minhas metas", "open_goals")),
                new InlineKeyboardRow(button("📊 Relatório de hoje", "open_report:today"))
        ));
    }

    public InlineKeyboardMarkup criarBotoesRefeicao(Long mealId, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        return markup(List.of(
                new InlineKeyboardRow(
                        button("✏️ Editar", "edit_meal:" + mealId),
                        button("🗑️ Excluir", "delete_meal:" + mealId)
                ),
                new InlineKeyboardRow(button("📊 Ver relatório", "open_report:" + safeDate))
        ));
    }

    public InlineKeyboardMarkup criarBotoesConfirmarExclusaoRefeicao(Long mealId) {
        return markup(List.of(new InlineKeyboardRow(
                button("🗑️ Sim, excluir", "cfm_del_meal:" + mealId),
                button("✖️ Voltar", "cnl_del_meal:" + mealId)
        )));
    }

    public InlineKeyboardMarkup criarBotaoVerRelatorio(LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        return markup(List.of(new InlineKeyboardRow(button("📊 Ver relatório", "open_report:" + safeDate))));
    }

    public InlineKeyboardMarkup criarBotoesConfirmacaoEntrada(String token) {
        return markup(List.of(
                new InlineKeyboardRow(
                        button("🥗 Refeição", "pending_meal:" + token),
                        button("💪 Exercício", "pending_exercise:" + token)
                ),
                new InlineKeyboardRow(button("✖️ Cancelar", "pending_cancel:" + token))
        ));
    }

    public InlineKeyboardMarkup criarBotaoCancelarEstado() {
        return markup(List.of(new InlineKeyboardRow(button("✖️ Cancelar", "cancel_state"))));
    }

    public InlineKeyboardMarkup criarBotoesConflitoTreino(String token) {
        return markup(List.of(
                new InlineKeyboardRow(button("✏️ Renomear treino atual", "wk_rename:" + token)),
                new InlineKeyboardRow(button("➕ Criar novo treino", "wk_new:" + token)),
                new InlineKeyboardRow(button("✖️ Cancelar", "pending_cancel:" + token))
        ));
    }

    public InlineKeyboardMarkup criarBotoesConfirmacaoMeta(GoalDto dto) {
        int cal = dto.getCalories() != null ? dto.getCalories() : 0;
        int prot = dto.getProtein() != null ? dto.getProtein() : 0;
        int carb = dto.getCarbs() != null ? dto.getCarbs() : 0;
        int fat = dto.getFat() != null ? dto.getFat() : 0;
        String payload = String.format("confirm_goal:%d:%d:%d:%d", cal, prot, carb, fat);
        return markup(List.of(new InlineKeyboardRow(
                button("✅ Aplicar metas", payload),
                button("✖️ Cancelar", "cancel_state")
        )));
    }

    public InlineKeyboardMarkup criarBotoesExercicios(Long workoutId, List<WorkoutDto.ExerciseDto> exercises) {
        return criarBotoesExercicios(workoutId, exercises, LocalDate.now(appZoneId));
    }

    public InlineKeyboardMarkup criarBotoesExercicios(Long workoutId, List<WorkoutDto.ExerciseDto> exercises, LocalDate date) {
        if (exercises == null || exercises.isEmpty()) {
            return null;
        }
        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        int count = exercises.size();

        for (int i = 0; i < count; i++) {
            WorkoutDto.ExerciseDto ex = exercises.get(i);
            String ref = (ex.getId() != null && !ex.getId().isBlank()) ? ex.getId() : String.valueOf(i);
            String suffix = count > 1 ? " " + (i + 1) : "";

            rows.add(new InlineKeyboardRow(
                    button("✏️ Editar" + suffix, "edit_ex:" + workoutId + ":" + ref),
                    button("🗑️ Excluir" + suffix, "delete_ex:" + workoutId + ":" + ref)
            ));
        }
        rows.add(new InlineKeyboardRow(button("📊 Ver relatório", "open_report:" + safeDate)));
        return markup(rows);
    }

    public InlineKeyboardMarkup criarBotoesConfirmarExclusaoExercicio(Long workoutId, String exerciseRef) {
        return markup(List.of(new InlineKeyboardRow(
                button("🗑️ Sim, excluir", "cfm_del_ex:" + workoutId + ":" + exerciseRef),
                button("✖️ Voltar", "cnl_del_ex:" + workoutId + ":" + exerciseRef)
        )));
    }

    public InlineKeyboardMarkup criarBotoesRelatorio(DailyReportDto report, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardRow navRow = new InlineKeyboardRow();
        navRow.add(button("⬅️ Anterior", "rep_nav:" + safeDate.minusDays(1)));
        if (safeDate.isBefore(LocalDate.now(appZoneId))) {
            navRow.add(button("Próximo ➡️", "rep_nav:" + safeDate.plusDays(1)));
        }
        rows.add(navRow);

        boolean hasMeals = report != null && report.getMeals() != null && !report.getMeals().isEmpty();
        boolean hasWorkouts = report != null && report.getWorkouts() != null && !report.getWorkouts().isEmpty();
        if (hasMeals || hasWorkouts) {
            rows.add(new InlineKeyboardRow(button("🗑️ Gerenciar itens", "rep_manage:" + safeDate)));
        }

        return markup(rows);
    }

    public InlineKeyboardMarkup criarBotoesGerenciarRelatorio(DailyReportDto report, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        List<InlineKeyboardRow> rows = new ArrayList<>();

        List<Meal> meals = (report != null && report.getMeals() != null) ? report.getMeals() : List.of();
        int idx = 1;
        for (Meal meal : meals) {
            rows.add(new InlineKeyboardRow(button("🗑️ Refeição " + idx, "ask_del_rep_m:" + meal.getId())));
            idx++;
        }

        List<WorkoutSession> workouts = (report != null && report.getWorkouts() != null) ? report.getWorkouts() : List.of();
        int workoutIdx = 1;
        for (WorkoutSession workout : workouts) {
            String raw = workout.getRawInput();
            String titulo = (raw != null && !raw.isBlank() && !raw.startsWith("[")) ? raw.trim() : "Treino";
            String baseLabel = workouts.size() > 1 ? titulo + " (" + workoutIdx + ")" : titulo;
            String label = "🗑️ " + truncateLabel(baseLabel, MAX_DYNAMIC_LABEL_LENGTH);
            rows.add(new InlineKeyboardRow(button(label, "ask_del_rep_w:" + workout.getId())));
            workoutIdx++;
        }

        rows.add(new InlineKeyboardRow(button("⬅️ Voltar", "rep_view:" + safeDate)));

        return markup(rows);
    }

    public InlineKeyboardMarkup criarBotoesConfirmarExclusaoRelatorio(String confirmCallbackData, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now(appZoneId);
        return markup(List.of(new InlineKeyboardRow(
                button("🗑️ Sim, excluir", confirmCallbackData),
                button("✖️ Voltar", "rep_manage:" + safeDate)
        )));
    }

    String truncateLabel(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen || maxLen < 2) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen - 1).trim() + "…";
    }

    private InlineKeyboardMarkup markup(List<InlineKeyboardRow> rows) {
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}
