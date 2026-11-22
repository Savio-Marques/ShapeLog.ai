package com.bot.telegram.bot.keyboard;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.WorkoutSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InlineKeyboardFactoryTest {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");
    private InlineKeyboardFactory factory;

    @BeforeEach
    void setUp() {
        factory = new InlineKeyboardFactory(APP_ZONE);
    }

    @Test
    void criarBotoesStart_deveConterAtalhosMetasERelatorioHoje() {
        InlineKeyboardMarkup kb = factory.criarBotoesStart();
        List<InlineKeyboardButton> all = flatten(kb);
        assertEquals(2, all.size());
        assertEquals("open_goals", all.get(0).getCallbackData());
        assertEquals("open_report:today", all.get(1).getCallbackData());
        assertAllCallbackDataWithin64Bytes(kb);
    }

    @Test
    void criarBotoesExercicios_deveConterBotaoVerRelatorio() {
        LocalDate date = LocalDate.of(2026, 11, 21);
        WorkoutDto.ExerciseDto ex = WorkoutDto.ExerciseDto.builder().id("a1b2c3d4").name("Supino").build();
        InlineKeyboardMarkup kb = factory.criarBotoesExercicios(99L, List.of(ex), date);

        List<InlineKeyboardButton> all = flatten(kb);
        assertEquals(3, all.size());
        assertEquals("edit_ex:99:a1b2c3d4", all.get(0).getCallbackData());
        assertEquals("delete_ex:99:a1b2c3d4", all.get(1).getCallbackData());
        assertEquals("open_report:2026-11-21", all.get(2).getCallbackData());
        assertAllCallbackDataWithin64Bytes(kb);
    }

    @Test
    void criarBotoesConfirmacaoExclusao_devemRespeitarLimite64Bytes() {
        long bigId = Long.MAX_VALUE;
        LocalDate date = LocalDate.of(2026, 11, 21);

        InlineKeyboardMarkup mealKb = factory.criarBotoesConfirmarExclusaoRefeicao(bigId);
        assertEquals("cfm_del_meal:" + bigId, flatten(mealKb).get(0).getCallbackData());
        assertEquals("cnl_del_meal:" + bigId, flatten(mealKb).get(1).getCallbackData());
        assertAllCallbackDataWithin64Bytes(mealKb);

        InlineKeyboardMarkup exKb = factory.criarBotoesConfirmarExclusaoExercicio(bigId, "a1b2c3d4");
        assertEquals("cfm_del_ex:" + bigId + ":a1b2c3d4", flatten(exKb).get(0).getCallbackData());
        assertEquals("cnl_del_ex:" + bigId + ":a1b2c3d4", flatten(exKb).get(1).getCallbackData());
        assertAllCallbackDataWithin64Bytes(exKb);

        InlineKeyboardMarkup repKb = factory.criarBotoesConfirmarExclusaoRelatorio("del_rep_m:" + bigId, date);
        assertEquals("del_rep_m:" + bigId, flatten(repKb).get(0).getCallbackData());
        assertEquals("rep_manage:2026-11-21", flatten(repKb).get(1).getCallbackData());
        assertAllCallbackDataWithin64Bytes(repKb);
    }

    @Test
    void criarBotoesGerenciarRelatorio_deveTruncarTitulosLongosEUsarCallbacksDeConfirmacao() {
        LocalDate date = LocalDate.of(2026, 11, 21);
        Meal m1 = Meal.builder().id(10L).build();
        WorkoutSession w1 = WorkoutSession.builder()
                .id(20L)
                .rawInput("Treino Super Pesado de Peito, Ombro e Tríceps")
                .build();
        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of(m1))
                .workouts(List.of(w1))
                .build();

        InlineKeyboardMarkup kb = factory.criarBotoesGerenciarRelatorio(report, date);
        List<InlineKeyboardButton> all = flatten(kb);
        assertEquals(3, all.size());
        assertEquals("ask_del_rep_m:10", all.get(0).getCallbackData());
        assertEquals("ask_del_rep_w:20", all.get(1).getCallbackData());
        assertTrue(all.get(1).getText().endsWith("…"));
        assertTrue(all.get(1).getText().length() <= InlineKeyboardFactory.MAX_DYNAMIC_LABEL_LENGTH + 4);
        assertEquals("rep_view:2026-11-21", all.get(2).getCallbackData());
        assertAllCallbackDataWithin64Bytes(kb);
    }

    @Test
    void criarBotoesConfirmacaoEntradaEMeta_devemTerCallbackDataValido() {
        assertAllCallbackDataWithin64Bytes(factory.criarBotoesConfirmacaoEntrada("abc12345"));
        assertAllCallbackDataWithin64Bytes(factory.criarBotoesConflitoTreino("abc12345"));
        assertAllCallbackDataWithin64Bytes(factory.criarBotoesConfirmacaoMeta(
                GoalDto.builder().calories(2200).protein(170).carbs(220).fat(65).build()));
        assertAllCallbackDataWithin64Bytes(factory.criarBotoesRelatorio(
                DailyReportDto.builder().meals(List.of(Meal.builder().id(1L).build())).workouts(List.of()).build(),
                LocalDate.now(APP_ZONE).minusDays(1)));
    }

    private List<InlineKeyboardButton> flatten(InlineKeyboardMarkup kb) {
        return kb.getKeyboard().stream().flatMap(List::stream).toList();
    }

    private void assertAllCallbackDataWithin64Bytes(InlineKeyboardMarkup kb) {
        for (InlineKeyboardButton btn : flatten(kb)) {
            assertNotNull(btn.getCallbackData());
            int len = btn.getCallbackData().getBytes(StandardCharsets.UTF_8).length;
            assertTrue(len <= 64, "Callback excedeu 64 bytes (" + len + "): " + btn.getCallbackData());
        }
    }
}
