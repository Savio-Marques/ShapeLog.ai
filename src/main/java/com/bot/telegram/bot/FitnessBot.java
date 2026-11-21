package com.bot.telegram.bot;

import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FitnessBot extends TelegramLongPollingBot implements BotActionSender {

    private static final Logger log = LoggerFactory.getLogger(FitnessBot.class);
    static final long MAX_VOICE_FILE_SIZE_BYTES = 2L * 1024 * 1024;
    static final int MAX_VOICE_DURATION_SECONDS = 120;
    static final long ADMIN_NOTIFY_COOLDOWN_MS = 24L * 60 * 60 * 1000L;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.admin-id:0}")
    private Long adminId;

    private final UserService userService;
    private final CommandRouter commandRouter;
    private final MessageFormatter messageFormatter;
    private final MeterRegistry meterRegistry;
    private final Executor botUpdateExecutor;
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> chatQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> adminNotifiedAt = new ConcurrentHashMap<>();

    public FitnessBot(UserService userService, CommandRouter commandRouter, MessageFormatter messageFormatter, MeterRegistry meterRegistry) {
        this(userService, commandRouter, messageFormatter, meterRegistry, Runnable::run);
    }

    @Autowired
    public FitnessBot(UserService userService, CommandRouter commandRouter, MessageFormatter messageFormatter, MeterRegistry meterRegistry,
                      @Qualifier("botUpdateExecutor") Executor botUpdateExecutor) {
        this.userService = userService;
        this.commandRouter = commandRouter;
        this.messageFormatter = messageFormatter;
        this.meterRegistry = meterRegistry;
        this.botUpdateExecutor = botUpdateExecutor;
    }

    @Override
    public String getBotUsername() { return this.botUsername; }

    @Override
    public String getBotToken() { return this.botToken; }

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
        enviarMensagem(chatId, "⛔ Acesso restrito. Solicite permissão ao administrador para utilizar o bot.");

        if (adminId != null && adminId > 0 && !adminId.equals(chatId)
                && shouldNotifyAdmin(chatId, System.currentTimeMillis())) {
            String username = user.getUsername() != null ? messageFormatter.escapeMarkdown(user.getUsername()) : "sem username";
            String firstName = user.getFirstName() != null ? messageFormatter.escapeMarkdown(user.getFirstName()) : "sem nome";

            String notifMsg = String.format(
                    "🔔 *Solicitação de Acesso*\n\n" +
                    "👤 *Nome:* %s\n" +
                    "🏷️ *Username:* @%s\n" +
                    "🆔 *ID:* `%d`\n\n" +
                    "Para aprovar, envie:\n`/aprovar %d`",
                    firstName, username, chatId, chatId
            );
            enviarMensagemMarkdown(adminId, notifMsg);
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
    public void onUpdateReceived(Update update) {
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
        Long chatId = null;
        try {
            if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
                meterRegistry.counter("shapelog.bot.updates", "type", "callback").increment();
                chatId = update.getCallbackQuery().getMessage().getChatId();
                UserTelegram user = userService.getOrCreateUser(
                        chatId,
                        update.getCallbackQuery().getFrom().getUserName(),
                        update.getCallbackQuery().getFrom().getFirstName()
                );
                if (!isUserAllowed(user, chatId)) {
                    responderCallback(update.getCallbackQuery().getId());
                    notificarAcessoNegadoEAdmin(user, chatId);
                    return;
                }
                int botMessageId = update.getCallbackQuery().getMessage().getMessageId();
                String data      = update.getCallbackQuery().getData();
                String cbId      = update.getCallbackQuery().getId();
                responderCallback(cbId);
                commandRouter.rotearCallback(user, data, botMessageId, chatId, this);
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
                        enviarMensagem(chatId, "⚠️ Áudio muito longo ou grande (máximo de 2 minutos / 2 MB). Envie um áudio mais curto ou descreva em texto.");
                        return;
                    }
                    byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                    if (audioBytes == null) {
                        enviarMensagem(chatId, "Não foi possível baixar o áudio. Tente reenviar ou descreva em texto.");
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
                enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro inesperado. Tente novamente em instantes.");
            }
        } finally {
            sample.stop(meterRegistry.timer("shapelog.bot.update.duration"));
        }
    }

    byte[] obterBytesDoAudio(String fileId) {
        File fileLocal = null;
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File fileTelegram = execute(getFile);
            fileLocal = downloadFile(fileTelegram);
            return Files.readAllBytes(fileLocal.toPath());
        } catch (Exception e) {
            log.error("Erro ao baixar áudio fileId={}", fileId, e);
            return null;
        } finally {
            if (fileLocal != null && fileLocal.exists()) {
                fileLocal.delete();
            }
        }
    }

    @Override
    public void enviarMensagem(long chatId, String texto) {
        enviarMensagemRetornando(chatId, texto);
    }

    @Override
    public Message enviarMensagemRetornando(long chatId, String texto) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        try {
            return execute(message);
        } catch (TelegramApiException e) {
            log.warn("Falha ao enviar mensagem simples para chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    @Override
    public void deletarMensagem(long chatId, int messageId) {
        DeleteMessage delete = new DeleteMessage();
        delete.setChatId(String.valueOf(chatId));
        delete.setMessageId(messageId);
        try {
            execute(delete);
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
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        message.setParseMode("MarkdownV2");
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }
        try {
            return execute(message);
        } catch (TelegramApiException e) {
            log.warn("Falha ao enviar mensagem MarkdownV2 para chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    @Override
    public void editarMensagemBot(long chatId, int messageId, String novoTexto, InlineKeyboardMarkup keyboard) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(novoTexto);
        edit.setParseMode("MarkdownV2");
        if (keyboard != null) {
            edit.setReplyMarkup(keyboard);
        }
        try {
            execute(edit);
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
    public String resolverMensagemDeErro(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof TimeoutException) {
                return "A IA demorou mais que o esperado. Tente novamente em instantes!";
            }
            if (cause instanceof OptimisticLockingFailureException) {
                return "Este treino foi alterado ao mesmo tempo por outra ação. Tente novamente!";
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

    private void responderCallback(String callbackQueryId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Falha ao confirmar callback id={}: {}", callbackQueryId, e.getMessage());
        }
    }
}
