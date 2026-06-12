package com.bot.telegram.bot;

import com.bot.telegram.bot.handler.MealHandler;
import com.bot.telegram.bot.handler.ReportHandler;
import com.bot.telegram.bot.handler.WorkoutHandler;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FitnessBot extends TelegramLongPollingBot implements BotActionSender {

    private static final Logger log = LoggerFactory.getLogger(FitnessBot.class);

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.allowed-users}")
    private String allowedUsersStr;

    private java.util.Set<Long> allowedUsersCache;

    @Autowired
    private UserService userService;

    @Autowired
    private MealHandler mealHandler;

    @Autowired
    private WorkoutHandler workoutHandler;

    @Autowired
    private ReportHandler reportHandler;

    @Autowired
    private MessageFormatter messageFormatter;

    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> userStateTimes = new ConcurrentHashMap<>();
    private static final long STATE_TTL_MS = 15 * 60 * 1000L;

    @Override
    public String getBotUsername() { return this.botUsername; }

    @Override
    public String getBotToken() { return this.botToken; }

    private boolean isUserAllowed(Long chatId) {
        if (allowedUsersCache == null) {
            allowedUsersCache = new java.util.HashSet<>();
            if (allowedUsersStr != null && !allowedUsersStr.trim().isEmpty()) {
                String[] ids = allowedUsersStr.split(",");
                for (String id : ids) {
                    try {
                        allowedUsersCache.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("ID inválido na lista de usuários permitidos: {}", id);
                    }
                }
            }
        }
        if (allowedUsersCache.isEmpty()) {
            return true;
        }
        return allowedUsersCache.contains(chatId);
    }

    private void putState(long chatId, String state) {
        userStates.put(chatId, state);
        userStateTimes.put(chatId, System.currentTimeMillis());
    }

    private String getState(long chatId) {
        Long timestamp = userStateTimes.get(chatId);
        if (timestamp == null || System.currentTimeMillis() - timestamp > STATE_TTL_MS) {
            userStates.remove(chatId);
            userStateTimes.remove(chatId);
            return null;
        }
        return userStates.get(chatId);
    }

    private void removeState(long chatId) {
        userStates.remove(chatId);
        userStateTimes.remove(chatId);
    }

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = null;
        try {
            if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
                if (!isUserAllowed(chatId)) {
                    log.warn("Acesso negado silenciomente via CallbackQuery para chatId={}", chatId);
                    return;
                }
                
                int botMessageId = update.getCallbackQuery().getMessage().getMessageId();
                String data = update.getCallbackQuery().getData();
                String callbackQueryId = update.getCallbackQuery().getId();

                UserTelegram user = userService.getOrCreateUser(
                        chatId,
                        update.getCallbackQuery().getFrom().getUserName(),
                        update.getCallbackQuery().getFrom().getFirstName()
                );

                responderCallback(callbackQueryId);
                processarCallbackQuery(user, data, botMessageId, chatId);
                return;
            }

            if (update.hasMessage()) {
                chatId = update.getMessage().getChatId();
                if (!isUserAllowed(chatId)) {
                    log.warn("Acesso negado silenciomente via Message para chatId={}", chatId);
                    return;
                }

                UserTelegram user = userService.getOrCreateUser(
                        chatId,
                        update.getMessage().getFrom().getUserName(),
                        update.getMessage().getFrom().getFirstName()
                );

                if (update.getMessage().hasText()) {
                    String text = update.getMessage().getText().trim();

                    if (text.startsWith("/")) {
                        removeState(chatId);

                        if (text.startsWith("/start")) {
                            enviarMensagemMarkdown(chatId, messageFormatter.formatStart(user.getFirstName()));
                        } else if (text.startsWith("/meta")) {
                            configurarMeta(user, text, chatId);
                        } else if (text.startsWith("/refeicao")) {
                            String descricao = text.replace("/refeicao", "").trim();
                            if (descricao.isEmpty()) {
                                putState(chatId, UserState.AWAITING_MEAL.serialize());
                                enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto sua refeição agora!");
                            } else {
                                mealHandler.registrarRefeicao(user, descricao, null, update.getMessage().getMessageId(), chatId, this);
                            }
                        } else if (text.startsWith("/treino")) {
                            String descricao = text.replace("/treino", "").trim();
                            if (descricao.isEmpty()) {
                                putState(chatId, UserState.AWAITING_WORKOUT.serialize());
                                enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto seu treino agora!");
                            } else {
                                workoutHandler.registrarTreino(user, descricao, null, update.getMessage().getMessageId(), chatId, this);
                            }
                        } else if (text.startsWith("/relatorio")) {
                            String arg = text.replace("/relatorio", "").trim();
                            reportHandler.gerarRelatorio(user, arg, chatId, this);
                        } else {
                            enviarMensagem(chatId, "Comando não reconhecido. Use /refeicao, /treino, /meta ou /relatorio.");
                        }
                    } else {
                        UserState state = UserState.from(getState(chatId));
                        String rawState = getState(chatId);
                        if (state != null) {
                            removeState(chatId);
                            if (state == UserState.AWAITING_MEAL) {
                                mealHandler.registrarRefeicao(user, text, null, update.getMessage().getMessageId(), chatId, this);
                            } else if (state == UserState.AWAITING_WORKOUT) {
                                workoutHandler.registrarTreino(user, text, null, update.getMessage().getMessageId(), chatId, this);
                            } else if (state == UserState.AWAITING_EDIT_MEAL) {
                                Long mealId = UserState.extractId(rawState);
                                mealHandler.atualizarRefeicaoEditada(mealId, text, null, chatId, this);
                            } else if (state == UserState.AWAITING_EDIT_WORKOUT) {
                                Long workoutId = UserState.extractId(rawState);
                                workoutHandler.atualizarTreinoEditado(workoutId, text, null, chatId, this);
                            }
                        } else {
                            enviarMensagem(chatId, "Por favor, envie primeiro o comando /refeicao ou /treino antes de descrever os alimentos ou exercícios.");
                        }
                    }


                } else if (update.getMessage().hasVoice()) {
                    Voice voice = update.getMessage().getVoice();
                    String rawVoiceState = getState(chatId);
                    UserState voiceState = UserState.from(rawVoiceState);
                    if (voiceState != null) {
                        removeState(chatId);

                        // verificar se o download do áudio foi bem-sucedido antes de prosseguir
                        byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                        if (audioBytes == null) {
                            enviarMensagem(chatId, "❌ Não foi possível baixar o áudio. Tente reenviar ou descreva em texto.");
                            return;
                        }

                        if (voiceState == UserState.AWAITING_MEAL) {
                            mealHandler.registrarRefeicao(user, null, audioBytes, update.getMessage().getMessageId(), chatId, this);
                        } else if (voiceState == UserState.AWAITING_WORKOUT) {
                            workoutHandler.registrarTreino(user, null, audioBytes, update.getMessage().getMessageId(), chatId, this);
                        } else if (voiceState == UserState.AWAITING_EDIT_MEAL) {
                            Long mealId = UserState.extractId(rawVoiceState);
                            mealHandler.atualizarRefeicaoEditada(mealId, null, audioBytes, chatId, this);
                        } else if (voiceState == UserState.AWAITING_EDIT_WORKOUT) {
                            Long workoutId = UserState.extractId(rawVoiceState);
                            workoutHandler.atualizarTreinoEditado(workoutId, null, audioBytes, chatId, this);
                        }
                    } else {
                        enviarMensagem(chatId, "Por favor, primeiro envie o comando correspondente (/refeicao ou /treino) e em seguida grave o áudio.");
                    }
                }
            }

        } catch (Exception e) {
            log.error("Erro inesperado ao processar update do chatId={}", chatId, e);
            if (chatId != null) {
                String errMsg = resolverMensagemDeErro(e);
                enviarMensagem(chatId, errMsg != null ? errMsg : "⚠️ Ocorreu um erro inesperado. Tente novamente em instantes.");
            }
        }
    }

    private void configurarMeta(UserTelegram user, String text, long chatId) {
        String[] partes = text.split("\\s+");
        if (partes.length < 5) {
            enviarMensagem(chatId, "Uso incorreto! Envie: /meta <calorias> <proteínas> <carbos> <gorduras>\nExemplo: /meta 2000 150 200 60");
            return;
        }
        try {
            int cal = Integer.parseInt(partes[1]);
            int prot = Integer.parseInt(partes[2]);
            int carb = Integer.parseInt(partes[3]);
            int fat = Integer.parseInt(partes[4]);
            userService.updateGoals(user, cal, prot, carb, fat);
            enviarMensagemMarkdown(chatId, messageFormatter.formatGoalsUpdated(cal, prot, carb, fat));
        } catch (NumberFormatException e) {
            enviarMensagem(chatId, "Erro: Os valores das metas devem ser números inteiros!");
        }
    }



    private byte[] obterBytesDoAudio(String fileId) {
        try {
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File fileTelegram = execute(getFile);
            File fileLocal = downloadFile(fileTelegram);
            byte[] bytes = Files.readAllBytes(fileLocal.toPath());
            fileLocal.delete();
            return bytes;
        } catch (Exception e) {
            log.error("Erro ao baixar áudio fileId={}", fileId, e);
            return null;
        }
    }

    @Override
    public void enviarMensagem(long chatId, String texto) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn("Falha ao enviar mensagem simples para chatId={}: {}", chatId, e.getMessage());
        }
    }


    @Override
    public org.telegram.telegrambots.meta.api.objects.Message enviarMensagemMarkdown(long chatId, String texto) {
        return enviarMensagemMarkdown(chatId, texto, null);
    }

    @Override
    public org.telegram.telegrambots.meta.api.objects.Message enviarMensagemMarkdown(long chatId, String texto, org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        message.setParseMode("Markdown");
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }
        try {
            return execute(message);
        } catch (TelegramApiException e) {
            log.warn("Falha ao enviar mensagem Markdown para chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }



    private void processarCallbackQuery(UserTelegram user, String data, int botMessageId, long chatId) {
        try {
            if (data.startsWith("delete_meal:")) {
                Long mealId = Long.parseLong(data.split(":")[1]);
                mealHandler.processarExclusaoRefeicao(mealId, botMessageId, chatId, this);
            } else if (data.startsWith("delete_workout:")) {
                Long workoutId = Long.parseLong(data.split(":")[1]);
                workoutHandler.processarExclusaoTreino(workoutId, botMessageId, chatId, this);
            } else if (data.startsWith("edit_meal:")) {
                Long mealId = Long.parseLong(data.split(":")[1]);
                putState(chatId, UserState.AWAITING_EDIT_MEAL.withId(mealId));
                enviarMensagem(chatId, "✏️ Envie um texto ou grave um áudio com o novo conteúdo para esta refeição.");
            } else if (data.startsWith("edit_workout:")) {
                Long workoutId = Long.parseLong(data.split(":")[1]);
                putState(chatId, UserState.AWAITING_EDIT_WORKOUT.withId(workoutId));
                enviarMensagem(chatId, "✏️ Envie um texto ou grave um áudio com o novo conteúdo para este treino.");
            }
        } catch (Exception e) {
            log.error("Erro ao processar callback data='{}' para chatId={}", data, chatId, e);
            enviarMensagem(chatId, "Ocorreu um erro ao processar sua solicitação.");
        }
    }



    @Override
    public void editarMensagemBot(long chatId, int messageId, String novoTexto, org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) {
        org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText edit = new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(novoTexto);
        edit.setParseMode("Markdown");
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

    private void responderCallback(String callbackQueryId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Falha ao confirmar callback id={}: {}", callbackQueryId, e.getMessage());
        }
    }

    @Override
    public String resolverMensagemDeErro(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null) {
                if (msg.contains("RESOURCE_EXHAUSTED") || msg.contains("429")) {
                    return "⏳ A IA está sobrecarregada no momento. Aguarde alguns segundos e tente novamente!";
                }
                if (cause instanceof IllegalArgumentException) {
                    return "⚠️ " + msg;
                }
            }
            cause = cause.getCause();
        }
        return null;
    }
}