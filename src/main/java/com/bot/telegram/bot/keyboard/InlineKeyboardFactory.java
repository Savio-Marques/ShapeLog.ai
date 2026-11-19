package com.bot.telegram.bot.keyboard;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class InlineKeyboardFactory {

    public InlineKeyboardMarkup criarBotoesRefeicao(Long mealId) {
        return criarBotoesRefeicao(mealId, LocalDate.now());
    }

    public InlineKeyboardMarkup criarBotoesRefeicao(Long mealId, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now();
        InlineKeyboardButton btnEdit = button("✏️ Editar", "edit_meal:" + mealId);
        InlineKeyboardButton btnDel = button("❌ Excluir", "delete_meal:" + mealId);
        InlineKeyboardButton btnReport = button("📊 Ver Relatório", "open_report:" + safeDate);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(btnEdit, btnDel),
                List.of(btnReport)
        ));
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesConfirmacaoEntrada(String token) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(
                        button("🥗 Refeição", "pending_meal:" + token),
                        button("💪 Exercício", "pending_exercise:" + token)
                ),
                List.of(button("✖️ Cancelar", "pending_cancel:" + token))
        ));
        return markup;
    }

    public InlineKeyboardMarkup criarBotaoCancelarEstado() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(button("✖️ Cancelar", "cancel_state"))));
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesConflitoTreino(String token) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(button("✏️ Renomear treino atual", "wk_rename:" + token)),
                List.of(button("➕ Criar novo treino", "wk_new:" + token)),
                List.of(button("✖️ Cancelar", "pending_cancel:" + token))
        ));
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesConfirmacaoMeta(GoalDto dto) {
        int cal = dto.getCalories() != null ? dto.getCalories() : 0;
        int prot = dto.getProtein() != null ? dto.getProtein() : 0;
        int carb = dto.getCarbs() != null ? dto.getCarbs() : 0;
        int fat = dto.getFat() != null ? dto.getFat() : 0;
        String payload = String.format("confirm_goal:%d:%d:%d:%d", cal, prot, carb, fat);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(
                button("✅ Aplicar Metas", payload),
                button("✖️ Cancelar", "cancel_state")
        )));
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesExercicios(Long workoutId, List<WorkoutDto.ExerciseDto> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return null;
        }
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        int count = exercises.size();

        for (int i = 0; i < count; i++) {
            WorkoutDto.ExerciseDto ex = exercises.get(i);
            String ref = (ex.getId() != null && !ex.getId().isBlank()) ? ex.getId() : String.valueOf(i);
            String suffix = count > 1 ? " " + (i + 1) : "";

            keyboard.add(List.of(
                    button("✏️ Editar" + suffix, "edit_ex:" + workoutId + ":" + ref),
                    button("❌ Excluir" + suffix, "delete_ex:" + workoutId + ":" + ref)
            ));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesExercicios(Long workoutId, int startIndex, int count) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            String suffix = count > 1 ? " " + (i + 1) : "";
            keyboard.add(List.of(
                    button("✏️ Editar" + suffix, "edit_ex:" + workoutId + ":" + idx),
                    button("❌ Excluir" + suffix, "delete_ex:" + workoutId + ":" + idx)
            ));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesRelatorio(DailyReportDto report) {
        return criarBotoesRelatorio(report, LocalDate.now());
    }

    public InlineKeyboardMarkup criarBotoesRelatorio(DailyReportDto report, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        navRow.add(button("⬅️ Dia Anterior", "rep_nav:" + safeDate.minusDays(1)));
        if (safeDate.isBefore(LocalDate.now())) {
            navRow.add(button("Dia Seguinte ➡️", "rep_nav:" + safeDate.plusDays(1)));
        }
        keyboard.add(navRow);

        boolean hasMeals = report != null && report.getMeals() != null && !report.getMeals().isEmpty();
        boolean hasWorkouts = report != null && report.getWorkouts() != null && !report.getWorkouts().isEmpty();
        if (hasMeals || hasWorkouts) {
            keyboard.add(List.of(button("🗑️ Gerenciar / Excluir Itens", "rep_manage:" + safeDate)));
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesGerenciarRelatorio(DailyReportDto report, LocalDate date) {
        LocalDate safeDate = date != null ? date : LocalDate.now();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<Meal> meals = (report != null && report.getMeals() != null) ? report.getMeals() : List.of();
        int idx = 1;
        for (Meal meal : meals) {
            keyboard.add(List.of(button("❌ Excluir Refeição " + idx, "del_rep_m:" + meal.getId())));
            idx++;
        }

        List<WorkoutSession> workouts = (report != null && report.getWorkouts() != null) ? report.getWorkouts() : List.of();
        int workoutIdx = 1;
        for (WorkoutSession workout : workouts) {
            String raw = workout.getRawInput();
            String titulo = (raw != null && !raw.isBlank() && !raw.startsWith("[")) ? raw.trim() : "Treino";
            String label = workouts.size() > 1
                    ? "❌ Excluir " + titulo + " (" + workoutIdx + ")"
                    : "❌ Excluir " + titulo;
            keyboard.add(List.of(button(label, "del_rep_w:" + workout.getId())));
            workoutIdx++;
        }

        keyboard.add(List.of(button("⬅️ Voltar ao Relatório", "rep_view:" + safeDate)));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}
