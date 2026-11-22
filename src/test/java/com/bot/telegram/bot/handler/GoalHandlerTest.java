package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.GeminiService;
import com.bot.telegram.service.IWorkoutService;
import com.bot.telegram.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoalHandlerTest {

    private UserService userService;
    private GeminiService geminiService;
    private MessageFormatter messageFormatter;
    private InlineKeyboardFactory keyboardFactory;
    private BotActionSender sender;
    private GoalHandler goalHandler;
    private UserTelegram user;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        geminiService = mock(GeminiService.class);
        messageFormatter = new MessageFormatter(mock(IWorkoutService.class));
        keyboardFactory = new InlineKeyboardFactory();
        sender = mock(BotActionSender.class);
        goalHandler = new GoalHandler(userService, geminiService, messageFormatter, keyboardFactory);

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
    void sugerirMetasPorIA_valido_naoDeveSalvarAntesDaConfirmacao() {
        Message tempMsg = new Message();
        tempMsg.setMessageId(42);
        when(sender.enviarMensagemRetornando(eq(100L), anyString())).thenReturn(tempMsg);

        GoalDto dto = GoalDto.builder()
                .calories(2200)
                .protein(170)
                .carbs(225)
                .fat(68)
                .explanation("Ajuste leve para manutenção.")
                .build();
        when(geminiService.parseGoal("quero 2200 kcal", null, 2000, 150, 200, 60)).thenReturn(dto);

        assertTrue(goalHandler.sugerirMetasPorIA(user, "quero 2200 kcal", null, 100L, sender));

        verify(userService, never()).updateGoals(any(), anyInt(), anyInt(), anyInt(), anyInt());
        verify(sender).deletarMensagem(100L, 42);
        verify(sender).enviarMensagemMarkdown(eq(100L), contains("2.200 kcal"), any(InlineKeyboardMarkup.class));
    }

    @Test
    void confirmarMetasSugeridas_deveSalvarNoBancoEAtualizarMensagemEToast() {
        goalHandler.confirmarMetasSugeridas(user, 2200, 170, 225, 68, 77, 100L, sender);

        verify(userService).updateGoals(user, 2200, 170, 225, 68);
        verify(sender).responderToast("Metas atualizadas!");
        verify(sender).editarMensagemBot(eq(100L), eq(77), contains("2.200 kcal"), isNull());
    }

    @Test
    void salvarMetasDiretas_comValorZero_deveRejeitarERetornarFalse() {
        assertFalse(goalHandler.salvarMetasDiretas(user, 0, 150, 200, 60, 100L, sender));
        verify(userService, never()).updateGoals(any(), anyInt(), anyInt(), anyInt(), anyInt());
        verify(sender).enviarMensagemMarkdown(eq(100L), contains("maiores que zero"));
    }
}
