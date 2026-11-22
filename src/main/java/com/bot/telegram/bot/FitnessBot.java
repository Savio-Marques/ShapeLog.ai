package com.bot.telegram.bot;

import com.bot.telegram.exception.RateLimitExceededException;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FitnessBot implements LongPollingUpdateConsumer, BotActionSender {

    private static final Logger log = LoggerFactory.getLogger(FitnessBot.class);
    static final long MAX_VOICE_FILE_SIZE_BYTES = 2L * 1024 * 1024;
    static final int MAX_VOICE_DURATION_SECONDS = 120;
    static final long ADMIN_NOTIFY_COOLDOWN_MS = 24L * 60 * 60 * 1000L;

    @Value("${telegram.bot.admin-id:0}")
    private Long adminId;

    private final TelegramClient telegramClient;
    private final UserService userService;
    private final CommandRouter commandRouter;
    private final MessageFormatter messageFormatter;
    private final MeterRegistry meterRegistry;
    private final Executor botUpdateExecutor;
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> chatQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> adminNotifiedAt = new ConcurrentHashMap<>();

    public FitnessBot(UserService userService, CommandRouter commandRouter, MessageFormatter messageFormatter, MeterRegistry meterRegistry) {
        this(null, userService, commandRouter, messageFormatter, meterRegistry, Runnable::run);
    }

    public FitnessBot(UserService userService, CommandRouter commandRouter, MessageFormatter messageFormatter, MeterRegistry meterRegistry,
                      Executor botUpdateExecutor) {
        this(null, userService, commandRouter, messageFormatter, meterRegistry, botUpdateExecutor);
    }

    @Autowired
    public FitnessBot(TelegramClient telegramClient, UserService userService, CommandRouter commandRouter,
                      MessageFormatter messageFormatter, MeterRegistry meterRegistry,
                      @Qualifier("botUpdateExecutor") Executor botUpdateExecutor) {
        this.telegramClient = telegramClient;
        this.userService = userService;
        this.commandRouter = commandRouter;
        this.messageFormatter = messageFormatter;
        this.meterRegistry = meterRegistry;
        this.botUpdateExecutor = botUpdateExecutor;
    }

    private boolean isUserAllowed(UserTelegram user, Long chatId) {
        if (adminId != null && adminId > 0 && adminId.equals(chatId)) {
            if (!Boolean.TRUE.equals(user.getApproved())) {
                userService.approveUser(chatId);
            }
            return true;
        }
        return Boolean.TRUE.equals(user.getApproved());
    }

    private void notificarAcessoNegadoEAdmin(UserTelegram user, Long chatId) {
        enviarMensagemMarkdown(chatId, messageFormatter.formatAccessDenied());

        if (adminId != null && adminId > 0 && !adminId.equals(chatId)
                && shouldNotifyAdmin(chatId, System.currentTimeMillis())) {
            enviarMensagemMarkdown(
                    adminId,
                    messageFormatter.formatAccessRequestForAdmin(user.getFirstName(), user.getUsername(), chatId)
            );
        }
    }

    boolean shouldNotifyAdmin(Long chatId, long now) {
        if (adminNotifiedAt.size() > 1000) {
            adminNotifiedAt.entrySet().removeIf(entry -> (now - entry.getValue()) >= ADMIN_NOTIFY_COOLDOWN_MS);
        }
        AtomicBoolean shouldNotify = new AtomicBoolean(false);
        adminNotifiedAt.compute(chatId, (id, lastNotified) -> {
            if (lastNotified == null || (now - lastNotified) >= ADMIN_NOTIFY_COOLDOWN_MS) {
                shouldNotify.set(true);
                return now;
            }
            return lastNotified;
        });
        return shouldNotify.get();
    }

    @Override
    public void consume(List<Update> updates) {
        if (updates == null) {
            return;
        }
        for (Update update : updates) {
            consume(update);
        }
    }

    public void consume(Update update) {
        Long chatId = extractChatId(update);
        if (chatId == null) {
            botUpdateExecutor.execute(() -> processUpdate(update));
            return;
        }
        CompletableFuture<Void> scheduled = chatQueues.compute(chatId, (id, previous) ->
                (previous == null || previous.isDone())
                        ? CompletableFuture.runAsync(() -> processUpdate(update), botUpdateExecutor)
                        : previous.handleAsync((ignored, err) -> {
                            processUpdate(update);
                            return null;
                        }, botUpdateExecutor)
        );
        scheduled.whenComplete((res, err) -> chatQueues.remove(chatId, scheduled));
    }

    private Long extractChatId(Update update) {
        if (update == null) {
            return null;
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        return null;
    }

    void processUpdate(Update update) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Long chatId = extractChatId(update);
        if (update != null && update.getUpdateId() != null) {
            MDC.put("updateId", String.valueOf(update.getUpdateId()));
        }
        if (chatId != null) {
            MDC.put("chatId", String.valueOf(chatId));
        }
        try {
            if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
                meterRegistry.counter("shapelog.bot.updates", "type", "callback").increment();
                chatId = update.getCallbackQuery().getMessage().getChatId();
                String cbId = update.getCallbackQuery().getId();
                UserTelegram user = userService.getOrCreateUser(
                        chatId,
                        update.getCallbackQuery().getFrom().getUserName(),
                        update.getCallbackQuery().getFrom().getFirstName()
                );
                if (!isUserAllowed(user, chatId)) {
                    responderCallback(cbId, null);
                    notificarAcessoNegadoEAdmin(user, chatId);
                    return;
                }
                int botMessageId = update.getCallbackQuery().getMessage().getMessageId();
                String data = update.getCallbackQuery().getData();
                CallbackScopedSender scopedSender = new CallbackScopedSender(cbId);
                try {
                    commandRouter.rotearCallback(user, data, botMessageId, chatId, scopedSender);
                } finally {
                    scopedSender.ensureAnswered();
                }
                return;
            }
            if (update.hasMessage()) {
                chatId = update.getMessage().getChatId();
                UserTelegram user = userService.getOrCreateUser(
                        chatId,
                        update.getMessage().getFrom().getUserName(),
                        update.getMessage().getFrom().getFirstName()
                );
                if (!isUserAllowed(user, chatId)) {
                    notificarAcessoNegadoEAdmin(user, chatId);
                    return;
                }
                if (update.getMessage().hasText()) {
                    meterRegistry.counter("shapelog.bot.updates", "type", "message").increment();
                    String text = update.getMessage().getText().trim();
                    int msgId   = update.getMessage().getMessageId();
                    if (text.startsWith("/")) {
                        commandRouter.rotearComando(user, text, msgId, chatId, this);
                    } else {
                        commandRouter.rotearEstadoTexto(user, text, msgId, chatId, this);
                    }
                } else if (update.getMessage().hasVoice()) {
                    meterRegistry.counter("shapelog.bot.updates", "type", "voice").increment();
                    Voice voice = update.getMessage().getVoice();
                    if ((voice.getDuration() != null && voice.getDuration() > MAX_VOICE_DURATION_SECONDS)
                            || (voice.getFileSize() != null && voice.getFileSize() > MAX_VOICE_FILE_SIZE_BYTES)) {
                        enviarMensagemMarkdown(chatId, messageFormatter.formatVoiceTooLarge());
                        return;
                    }
                    byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                    if (audioBytes == null) {
                        enviarMensagemMarkdown(chatId, messageFormatter.formatVoiceDownloadError());
                        return;
                    }
                    commandRouter.rotearEstadoVoz(user, audioBytes, chatId, this);
                } else {
                    meterRegistry.counter("shapelog.bot.updates", "type", "other").increment();
                }
            }
        } catch (Exception e) {
            meterRegistry.counter("shapelog.bot.errors").increment();
            log.error("Erro inesperado ao processar update do chatId={}", chatId, e);
            if (chatId != null) {
                String errMsg = resolverMensagemDeErro(e);
                enviarMensagemMarkdown(
                        chatId,
                        errMsg != null ? messageFormatter.formatWarning(errMsg) : messageFormatter.formatUnexpectedError()
                );
            }
        } finally {
            sample.stop(meterRegistry.timer("shapelog.bot.update.duration"));
            MDC.clear();
        }
    }

    byte[] obterBytesDoAudio(String fileId) {
        try {
            org.telegram.telegrambots.meta.api.objects.File fileTelegram = telegramClient.execute(
                    GetFile.builder().fileId(fileId).build()
            );
            try (InputStream is = telegramClient.downloadFileAsStream(fileTelegram)) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.error("Erro ao baixar áudio fileId={}", fileId, e);
            return null;
        }
    }

    @Override
    public void enviarMensagem(long chatId, String texto) {
        enviarMensagemRetornando(chatId, texto);
    }

    @Override
    public Message enviarMensagemRetornando(long chatId, String texto) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(texto)
                .build();
        try {
            return telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.warn("Falha ao enviar mensagem simples para chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    @Override
    public void deletarMensagem(long chatId, int messageId) {
        DeleteMessage delete = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();
        try {
            telegramClient.execute(delete);
        } catch (TelegramApiException e) {
            log.warn("Falha ao deletar mensagem id={} no chatId={}: {}", messageId, chatId, e.getMessage());
        }
    }

    @Override
    public Message enviarMensagemMarkdown(long chatId, String texto) {
        return enviarMensagemMarkdown(chatId, texto, null);
    }

    @Override
    public Message enviarMensagemMarkdown(long chatId, String texto, InlineKeyboardMarkup keyboard) {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(chatId)
                .text(texto)
                .parseMode("MarkdownV2");
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            return telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.warn("Falha ao enviar mensagem MarkdownV2 para chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    @Override
    public void editarMensagemBot(long chatId, int messageId, String novoTexto, InlineKeyboardMarkup keyboard) {
        EditMessageText.EditMessageTextBuilder<?, ?> builder = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(novoTexto)
                .parseMode("MarkdownV2");
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                log.debug("Mensagem id={} no chatId={} não foi modificada (conteúdo idêntico).", messageId, chatId);
                return;
            }
            log.warn("Falha ao editar mensagem id={} no chatId={}, enviando nova mensagem como fallback", messageId, chatId);
            enviarMensagemMarkdown(chatId, novoTexto, keyboard);
        }
    }

    @Override
    public void editarTecladoBot(long chatId, int messageId, InlineKeyboardMarkup keyboard) {
        EditMessageReplyMarkup edit = EditMessageReplyMarkup.builder()
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(keyboard)
                .build();
        try {
            telegramClient.execute(edit);
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                log.debug("Teclado da mensagem id={} no chatId={} não foi modificado.", messageId, chatId);
                return;
            }
            log.warn("Falha ao editar teclado da mensagem id={} no chatId={}: {}", messageId, chatId, e.getMessage());
        }
    }

    @Override
    public String resolverMensagemDeErro(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof TimeoutException) {
                return "A IA demorou mais que o esperado. Tente novamente em instantes!";
            }
            if (cause instanceof OptimisticLockingFailureException) {
                return "Este treino foi alterado ao mesmo tempo por outra ação. Tente novamente!";
            }
            if (cause instanceof RateLimitExceededException) {
                return cause.getMessage();
            }
            String msg = cause.getMessage();
            if (msg != null) {
                if (msg.contains("RESOURCE_EXHAUSTED") || msg.contains("429")) {
                    return "A IA está sobrecarregada no momento. Aguarde alguns segundos e tente novamente!";
                }
                if (cause instanceof IllegalArgumentException) {
                    return msg;
                }
            }
            cause = cause.getCause();
        }
        return null;
    }

    void responderCallback(String callbackQueryId, String text) {
        AnswerCallbackQuery.AnswerCallbackQueryBuilder<?, ?> builder = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId);
        if (text != null && !text.isBlank()) {
            String trimmed = text.trim();
            builder.text(trimmed.length() > 200 ? trimmed.substring(0, 199) + "…" : trimmed);
        }
        try {
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.warn("Falha ao confirmar callback id={}: {}", callbackQueryId, e.getMessage());
        }
    }

    /**
     * Garante que cada CallbackQuery receba exatamente uma resposta (toast ou fallback vazio).
     */
    private final class CallbackScopedSender implements BotActionSender {
        private final String callbackQueryId;
        private final AtomicBoolean answered = new AtomicBoolean(false);

        private CallbackScopedSender(String callbackQueryId) {
            this.callbackQueryId = callbackQueryId;
        }

        @Override public void enviarMensagem(long chatId, String texto) { FitnessBot.this.enviarMensagem(chatId, texto); }
        @Override public Message enviarMensagemRetornando(long chatId, String texto) { return FitnessBot.this.enviarMensagemRetornando(chatId, texto); }
        @Override public void deletarMensagem(long chatId, int messageId) { FitnessBot.this.deletarMensagem(chatId, messageId); }
        @Override public Message enviarMensagemMarkdown(long chatId, String texto) { return FitnessBot.this.enviarMensagemMarkdown(chatId, texto); }
        @Override public Message enviarMensagemMarkdown(long chatId, String texto, InlineKeyboardMarkup keyboard) { return FitnessBot.this.enviarMensagemMarkdown(chatId, texto, keyboard); }
        @Override public void editarMensagemBot(long chatId, int messageId, String novoTexto, InlineKeyboardMarkup keyboard) { FitnessBot.this.editarMensagemBot(chatId, messageId, novoTexto, keyboard); }
        @Override public void editarTecladoBot(long chatId, int messageId, InlineKeyboardMarkup keyboard) { FitnessBot.this.editarTecladoBot(chatId, messageId, keyboard); }
        @Override public String resolverMensagemDeErro(Exception e) { return FitnessBot.this.resolverMensagemDeErro(e); }

        @Override
        public void responderToast(String texto) {
            if (answered.compareAndSet(false, true)) {
                responderCallback(callbackQueryId, texto);
            }
        }

        void ensureAnswered() {
            if (answered.compareAndSet(false, true)) {
                responderCallback(callbackQueryId, null);
            }
        }
    }
}
