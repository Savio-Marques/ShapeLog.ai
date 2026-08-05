package com.bot.telegram.bot;

import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.UserService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class FitnessBot extends TelegramLongPollingBot implements BotActionSender {

    private static final Logger log = LoggerFactory.getLogger(FitnessBot.class);

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.allowed-users}")
    private String allowedUsersStr;

    private Set<Long> allowedUsersCache;

    private final UserService userService;
    private final CommandRouter commandRouter;

    public FitnessBot(UserService userService, CommandRouter commandRouter) {
        this.userService = userService;
        this.commandRouter = commandRouter;
    }

    @Override
    public String getBotUsername() { return this.botUsername; }

    @Override
    public String getBotToken() { return this.botToken; }

    @PostConstruct
    private void initAllowedUsers() {
        Set<Long> set = new HashSet<>();
        if (allowedUsersStr != null && !allowedUsersStr.trim().isEmpty()) {
            for (String id : allowedUsersStr.split(",")) {
                try {
                    set.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException e) {
                    log.warn("ID inválido na lista de usuários permitidos: {}", id);
                }
            }
        }
        this.allowedUsersCache = Collections.unmodifiableSet(set);
    }

    private boolean isUserAllowed(Long chatId) {
        return allowedUsersCache.isEmpty() || allowedUsersCache.contains(chatId);
    }

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = null;
        try {
            if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
                if (!isUserAllowed(chatId)) { return; }
                int botMessageId = update.getCallbackQuery().getMessage().getMessageId();
                String data      = update.getCallbackQuery().getData();
                String cbId      = update.getCallbackQuery().getId();
                UserTelegram user = userService.getOrCreateUser(
                        chatId,
                        update.getCallbackQuery().getFrom().getUserName(),
                        update.getCallbackQuery().getFrom().getFirstName()
                );
                responderCallback(cbId);
                commandRouter.rotearCallback(user, data, botMessageId, chatId, this);
                return;
            }
            if (update.hasMessage()) {
                chatId = update.getMessage().getChatId();
                if (!isUserAllowed(chatId)) { return; }
                UserTelegram user = userService.getOrCreateUser(
                        chatId,
                        update.getMessage().getFrom().getUserName(),
                        update.getMessage().getFrom().getFirstName()
                );
                if (update.getMessage().hasText()) {
                    String text = update.getMessage().getText().trim();
                    int msgId   = update.getMessage().getMessageId();
                    if (text.startsWith("/")) {
                        commandRouter.rotearComando(user, text, msgId, chatId, this);
                    } else {
                        commandRouter.rotearEstadoTexto(user, text, msgId, chatId, this);
                    }
                } else if (update.getMessage().hasVoice()) {
                    Voice voice = update.getMessage().getVoice();
                    byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                    if (audioBytes == null) {
                        enviarMensagem(chatId, "Não foi possível baixar o áudio. Tente reenviar ou descreva em texto.");
                        return;
                    }
                    commandRouter.rotearEstadoVoz(user, audioBytes, chatId, this);
                }
            }
        } catch (Exception e) {
            log.error("Erro inesperado ao processar update do chatId={}", chatId, e);
            if (chatId != null) {
                String errMsg = resolverMensagemDeErro(e);
                enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro inesperado. Tente novamente em instantes.");
            }
        }
    }

    private byte[] obterBytesDoAudio(String fileId) {
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
            log.warn("Falha ao editar mensagem id={} no chatId={}, enviando nova mensagem como fallback", messageId, chatId);
            enviarMensagemMarkdown(chatId, novoTexto, keyboard);
        }
    }

    @Override
    public String resolverMensagemDeErro(Exception e) {
        Throwable cause = e;
        while (cause != null) {
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
