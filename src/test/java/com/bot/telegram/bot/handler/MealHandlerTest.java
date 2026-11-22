package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.IWorkoutService;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MealHandlerTest {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    private MealService mealService;
    private ReportService reportService;
    private MessageFormatter messageFormatter;
    private InlineKeyboardFactory keyboardFactory;
    private BotActionSender sender;
    private MealHandler mealHandler;
    private UserTelegram user;

    @BeforeEach
    void setUp() {
        mealService = mock(MealService.class);
        reportService = mock(ReportService.class);
        messageFormatter = new MessageFormatter(mock(IWorkoutService.class), APP_ZONE);
        keyboardFactory = new InlineKeyboardFactory(APP_ZONE);
        sender = mock(BotActionSender.class);
        mealHandler = new MealHandler(mealService, reportService, messageFormatter, keyboardFactory, APP_ZONE);

        user = UserTelegram.builder()
                .id(100L)
                .firstName("Sávio")
                .targetCalories(2000)
                .targetProtein(150)
                .targetCarbs(200)
                .targetFat(60)
                .build();
    }

    @Test
    void registrarRefeicao_deveIncluirResumoDiarioNoCardESalvarBotMessageId() {
        Message tempMsg = new Message();
        tempMsg.setMessageId(10);
        Message sentBotMsg = new Message();
        sentBotMsg.setMessageId(20);

        LocalDate today = LocalDate.now(APP_ZONE);
        Meal saved = Meal.builder()
                .id(1L)
                .description("2 ovos mexidos (~100g)")
                .calories(200)
                .protein(14.0)
                .carbs(1.0)
                .fat(15.0)
                .createdAt(Instant.now())
                .build();

        DailyReportDto report = DailyReportDto.builder()
                .user(user)
                .meals(List.of(saved))
                .workouts(List.of())
                .totalCalories(800)
                .totalProtein(60.0)
                .totalCarbs(80.0)
                .totalFat(25.0)
                .build();

        when(sender.enviarMensagemRetornando(eq(100L), anyString())).thenReturn(tempMsg);
        when(mealService.registerMeal(user, "2 ovos", null, 5)).thenReturn(saved);
        when(reportService.getReportForDate(user, today)).thenReturn(report);
        when(sender.enviarMensagemMarkdown(eq(100L), anyString(), any(InlineKeyboardMarkup.class))).thenReturn(sentBotMsg);

        mealHandler.registrarRefeicao(user, "2 ovos", null, 5, 100L, sender);

        verify(sender).deletarMensagem(100L, 10);
        verify(sender).enviarMensagemMarkdown(
                eq(100L),
                argThat(msg -> msg.contains("200 kcal") && msg.contains("800 / 2000 kcal")),
                any(InlineKeyboardMarkup.class)
        );
        verify(mealService).saveBotMessageId(1L, 20);
    }

    @Test
    void atualizarRefeicaoEditada_deveEditarCardInPlaceComProgressoAtualizado() {
        Message tempMsg = new Message();
        tempMsg.setMessageId(11);
        LocalDate today = LocalDate.now(APP_ZONE);

        Meal updated = Meal.builder()
                .id(1L)
                .description("3 ovos mexidos (~150g)")
                .calories(300)
                .protein(21.0)
                .carbs(1.5)
                .fat(22.0)
                .botMessageId(20)
                .createdAt(Instant.now())
                .build();

        DailyReportDto report = DailyReportDto.builder()
                .user(user)
                .meals(List.of(updated))
                .workouts(List.of())
                .totalCalories(900)
                .totalProtein(67.0)
                .totalCarbs(80.5)
                .totalFat(32.0)
                .build();

        when(sender.enviarMensagemRetornando(eq(100L), anyString())).thenReturn(tempMsg);
        when(mealService.updateMeal(1L, "foram 3 ovos", null)).thenReturn(updated);
        when(reportService.getReportForDate(user, today)).thenReturn(report);

        mealHandler.atualizarRefeicaoEditada(user, 1L, "foram 3 ovos", null, 100L, sender);

        verify(sender).deletarMensagem(100L, 11);
        verify(sender).editarMensagemBot(
                eq(100L),
                eq(20),
                argThat(msg -> msg.contains("300 kcal") && msg.contains("900 / 2000 kcal")),
                any(InlineKeyboardMarkup.class)
        );
        verify(sender).enviarMensagem(eq(100L), contains("atualizada"));
    }
}
