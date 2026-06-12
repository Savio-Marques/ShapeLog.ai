package com.bot.telegram.bot.keyboard;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
public class InlineKeyboardFactory {

    public InlineKeyboardMarkup criarBotoesRefeicao(Long mealId) {
        InlineKeyboardButton btnEdit = new InlineKeyboardButton();
        btnEdit.setText("✏️ Editar");
        btnEdit.setCallbackData("edit_meal:" + mealId);

        InlineKeyboardButton btnDel = new InlineKeyboardButton();
        btnDel.setText("❌ Excluir");
        btnDel.setCallbackData("delete_meal:" + mealId);

        List<InlineKeyboardButton> row = List.of(btnEdit, btnDel);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesTreino(Long workoutId) {
        InlineKeyboardButton btnEdit = new InlineKeyboardButton();
        btnEdit.setText("✏️ Editar");
        btnEdit.setCallbackData("edit_workout:" + workoutId);

        InlineKeyboardButton btnDel = new InlineKeyboardButton();
        btnDel.setText("❌ Excluir");
        btnDel.setCallbackData("delete_workout:" + workoutId);

        List<InlineKeyboardButton> row = List.of(btnEdit, btnDel);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesExercicios(Long workoutId, int startIndex, int count) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            InlineKeyboardButton btnEdit = new InlineKeyboardButton();
            btnEdit.setText("✏️ Editar" + (count > 1 ? " " + (i+1) : ""));
            btnEdit.setCallbackData("edit_ex:" + workoutId + ":" + idx);

            InlineKeyboardButton btnDel = new InlineKeyboardButton();
            btnDel.setText("❌ Excluir" + (count > 1 ? " " + (i+1) : ""));
            btnDel.setCallbackData("delete_ex:" + workoutId + ":" + idx);
            
            keyboard.add(List.of(btnEdit, btnDel));
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    public InlineKeyboardMarkup criarBotoesRelatorio(DailyReportDto report) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        int idx = 1;
        for (Meal meal : report.getMeals()) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("❌ Excluir Refeição " + idx);
            btn.setCallbackData("delete_meal:" + meal.getId());
            keyboard.add(List.of(btn));
            idx++;
        }

        for (WorkoutSession workout : report.getWorkouts()) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("❌ Excluir Treino");
            btn.setCallbackData("delete_workout:" + workout.getId());
            keyboard.add(List.of(btn));
        }

        if (keyboard.isEmpty()) {
            return null;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }
}
