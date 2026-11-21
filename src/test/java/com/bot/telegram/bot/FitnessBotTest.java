package com.bot.telegram.bot;

import com.bot.telegram.exception.RateLimitExceededException;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.IWorkoutService;
import com.bot.telegram.service.UserService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FitnessBotTest {

    private TelegramClient telegramClient;
    private UserService userService;
    private CommandRouter commandRouter;
    private MessageFormatter messageFormatter;

    @BeforeEach
    void setUp() {
        telegramClient = mock(TelegramClient.class);
        userService = mock(UserService.class);
        commandRouter = mock(CommandRouter.class);
        messageFormatter = new MessageFormatter(mock(IWorkoutService.class));
    }

    private FitnessBot createSpyBot(Executor executor) {
        FitnessBot bot = spy(new FitnessBot(telegramClient, userService, commandRouter, messageFormatter, new SimpleMeterRegistry(), executor));
        ReflectionTestUtils.setField(bot, "adminId", 999999L);
        doNothing().when(bot).enviarMensagem(anyLong(), anyString());
        doReturn(null).when(bot).enviarMensagemMarkdown(anyLong(), anyString());
        return bot;
    }

    private Update buildVoiceUpdate(long chatId, int durationSec, long fileSizeBytes) {
        Voice voice = new Voice();
        voice.setFileId("voice-file-1");
        voice.setDuration(durationSec);
        voice.setFileSize(fileSizeBytes);

        return buildMessageUpdate(chatId, null, voice);
    }

    private Update buildTextUpdate(long chatId, String text) {
        return buildMessageUpdate(chatId, text, null);
    }

    private Update buildMessageUpdate(long chatId, String text, Voice voice) {
        User from = User.builder()
                .id(chatId)
                .userName("user" + chatId)
                .firstName("User " + chatId)
                .isBot(false)
                .build();

        Chat chat = Chat.builder()
                .id(chatId)
                .type("private")
                .build();

        Message message = new Message();
        message.setMessageId(1);
        message.setChat(chat);
        message.setFrom(from);
        if (text != null) {
            message.setText(text);
        }
        if (voice != null) {
            message.setVoice(voice);
        }

        Update update = new Update();
        update.setUpdateId(77);
        update.setMessage(message);
        return update;
    }

    @Test
    void processUpdate_audioAcimaDoLimiteDeDuracaoOuTamanho_deveRejeitarSemBaixar() {
        FitnessBot bot = createSpyBot(Runnable::run);
        UserTelegram approvedUser = UserTelegram.builder().id(100L).firstName("Sávio").approved(true).build();
        when(userService.getOrCreateUser(eq(100L), anyString(), anyString())).thenReturn(approvedUser);

        bot.consume(buildVoiceUpdate(100L, 121, 500_000L));
        bot.consume(buildVoiceUpdate(100L, 30, 3_000_000L));

        verify(bot, times(2)).enviarMensagem(eq(100L), contains("Áudio muito longo ou grande"));
        verify(bot, never()).obterBytesDoAudio(anyString());
        verifyNoInteractions(commandRouter);
    }

    @Test
    void processUpdate_usuarioNaoAprovado_deveRespeitarCooldownDeNotificacaoAoAdmin() {
        FitnessBot bot = createSpyBot(Runnable::run);
        UserTelegram unapproved = UserTelegram.builder()
                .id(111L)
                .username("visitante")
                .firstName("Visitante")
                .approved(false)
                .build();
        when(userService.getOrCreateUser(eq(111L), anyString(), anyString())).thenReturn(unapproved);

        bot.consume(buildTextUpdate(111L, "oi 1"));
        bot.consume(buildTextUpdate(111L, "oi 2"));

        verify(bot, times(2)).enviarMensagem(eq(111L), contains("Acesso restrito"));
        verify(bot, times(1)).enviarMensagemMarkdown(eq(999999L), contains("Solicitação de Acesso"));
        verifyNoInteractions(commandRouter);
    }

    @Test
    void consume_deveSerializarPorChatIdEPermitirParalelismoEntreChatsDistintos() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            FitnessBot bot = createSpyBot(executor);
            UserTelegram user1 = UserTelegram.builder().id(101L).firstName("U1").approved(true).build();
            UserTelegram user2 = UserTelegram.builder().id(202L).firstName("U2").approved(true).build();
            when(userService.getOrCreateUser(eq(101L), anyString(), anyString())).thenReturn(user1);
            when(userService.getOrCreateUser(eq(202L), anyString(), anyString())).thenReturn(user2);

            List<String> events = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch allowFirstToFinish = new CountDownLatch(1);
            CountDownLatch otherChatDone = new CountDownLatch(1);
            CountDownLatch secondSameChatDone = new CountDownLatch(1);

            doAnswer(inv -> {
                long chatId = inv.getArgument(3);
                String text = inv.getArgument(1);
                assertEquals(String.valueOf(chatId), MDC.get("chatId"));
                events.add(chatId + ":" + text + ":start");
                if (chatId == 101L && "msg1".equals(text)) {
                    firstEntered.countDown();
                    assertTrue(allowFirstToFinish.await(5, TimeUnit.SECONDS));
                    events.add("101:msg1:end");
                } else if (chatId == 101L && "msg2".equals(text)) {
                    events.add("101:msg2:end");
                    secondSameChatDone.countDown();
                } else if (chatId == 202L) {
                    events.add("202:msgA:end");
                    otherChatDone.countDown();
                }
                return null;
            }).when(commandRouter).rotearEstadoTexto(any(), anyString(), anyInt(), anyLong(), any());

            bot.consume(buildTextUpdate(101L, "msg1"));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

            bot.consume(buildTextUpdate(101L, "msg2"));
            bot.consume(buildTextUpdate(202L, "msgA"));

            assertTrue(otherChatDone.await(5, TimeUnit.SECONDS));
            assertFalse(events.contains("101:msg2:start"), "Segundo update do mesmo chatId nao pode iniciar antes do primeiro terminar");

            allowFirstToFinish.countDown();
            assertTrue(secondSameChatDone.await(5, TimeUnit.SECONDS));

            int endMsg1 = events.indexOf("101:msg1:end");
            int startMsg2 = events.indexOf("101:msg2:start");
            int endChat2 = events.indexOf("202:msgA:end");
            assertTrue(endMsg1 >= 0 && startMsg2 > endMsg1);
            assertTrue(endChat2 >= 0 && endChat2 < endMsg1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void processUpdate_deveLimparMdcAposExecucao() {
        FitnessBot bot = createSpyBot(Runnable::run);
        UserTelegram approvedUser = UserTelegram.builder().id(100L).firstName("Sávio").approved(true).build();
        when(userService.getOrCreateUser(eq(100L), anyString(), anyString())).thenReturn(approvedUser);

        bot.consume(buildTextUpdate(100L, "arroz e frango"));

        assertNull(MDC.get("chatId"));
        assertNull(MDC.get("updateId"));
    }

    @Test
    void resolverMensagemDeErro_optimisticLockingERateLimit_devemRetornarMensagensAmigaveis() {
        FitnessBot bot = createSpyBot(Runnable::run);
        assertEquals(
                "Este treino foi alterado ao mesmo tempo por outra ação. Tente novamente!",
                bot.resolverMensagemDeErro(new RuntimeException("wrapper", new OptimisticLockingFailureException("conflito")))
        );
        assertEquals(
                "⏳ Muitas solicitações em sequência!",
                bot.resolverMensagemDeErro(new RuntimeException("wrapper", new RateLimitExceededException("⏳ Muitas solicitações em sequência!")))
        );
    }
}
