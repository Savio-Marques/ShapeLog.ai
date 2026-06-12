package com.bot.telegram.bot;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.ReportService;
import com.bot.telegram.service.UserService;
import com.bot.telegram.service.WorkoutService;
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
public class FitnessBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Autowired
    private UserService userService;

    @Autowired
    private MealService mealService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private MessageFormatter messageFormatter;

    // Estado com TTL de 15 minutos para evitar estados presos
    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> userStateTimes = new ConcurrentHashMap<>();
    private static final long STATE_TTL_MS = 15 * 60 * 1000L;

    @Override
    public String getBotUsername() { return this.botUsername; }

    @Override
    public String getBotToken() { return this.botToken; }

    // --- Helpers de estado com TTL ---

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
        // Item 2: chatId extraído no escopo externo para poder avisar o usuário em caso de erro inesperado
        Long chatId = null;
        try {
            if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
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
                                putState(chatId, "AWAITING_MEAL");
                                enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto sua refeição agora!");
                            } else {
                                registrarRefeicao(user, descricao, null, update.getMessage().getMessageId(), chatId);
                            }
                        } else if (text.startsWith("/treino")) {
                            String descricao = text.replace("/treino", "").trim();
                            if (descricao.isEmpty()) {
                                putState(chatId, "AWAITING_WORKOUT");
                                enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto seu treino agora!");
                            } else {
                                registrarTreino(user, descricao, null, update.getMessage().getMessageId(), chatId);
                            }
                        } else if (text.startsWith("/relatorio")) {
                            String arg = text.replace("/relatorio", "").trim();
                            gerarRelatorio(user, arg, chatId);
                        } else {
                            enviarMensagem(chatId, "Comando não reconhecido. Use /refeicao, /treino, /meta ou /relatorio.");
                        }
                    } else {
                        String state = getState(chatId);
                        if (state != null) {
                            removeState(chatId);
                            if ("AWAITING_MEAL".equals(state)) {
                                registrarRefeicao(user, text, null, update.getMessage().getMessageId(), chatId);
                            } else if ("AWAITING_WORKOUT".equals(state)) {
                                registrarTreino(user, text, null, update.getMessage().getMessageId(), chatId);
                            } else if (state.startsWith("AWAITING_EDIT_MEAL:")) {
                                Long mealId = Long.parseLong(state.split(":")[1]);
                                atualizarRefeicaoEditada(mealId, text, null, chatId);
                            } else if (state.startsWith("AWAITING_EDIT_WORKOUT:")) {
                                Long workoutId = Long.parseLong(state.split(":")[1]);
                                atualizarTreinoEditado(workoutId, text, null, chatId);
                            }
                        } else {
                            enviarMensagem(chatId, "Por favor, envie primeiro o comando /refeicao ou /treino antes de descrever os alimentos ou exercícios.");
                        }
                    }

                } else if (update.getMessage().hasVoice()) {
                    Voice voice = update.getMessage().getVoice();
                    String state = getState(chatId);
                    if (state != null) {
                        removeState(chatId);

                        // Item 1: verificar se o download do áudio foi bem-sucedido antes de prosseguir
                        byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                        if (audioBytes == null) {
                            enviarMensagem(chatId, "❌ Não foi possível baixar o áudio. Tente reenviar ou descreva em texto.");
                            return;
                        }

                        if ("AWAITING_MEAL".equals(state)) {
                            registrarRefeicao(user, null, audioBytes, update.getMessage().getMessageId(), chatId);
                        } else if ("AWAITING_WORKOUT".equals(state)) {
                            registrarTreino(user, null, audioBytes, update.getMessage().getMessageId(), chatId);
                        } else if (state.startsWith("AWAITING_EDIT_MEAL:")) {
                            Long mealId = Long.parseLong(state.split(":")[1]);
                            atualizarRefeicaoEditada(mealId, null, audioBytes, chatId);
                        } else if (state.startsWith("AWAITING_EDIT_WORKOUT:")) {
                            Long workoutId = Long.parseLong(state.split(":")[1]);
                            atualizarTreinoEditado(workoutId, null, audioBytes, chatId);
                        }
                    } else {
                        enviarMensagem(chatId, "Por favor, primeiro envie o comando correspondente (/refeicao ou /treino) e em seguida grave o áudio.");
                    }
                }
            }

        } catch (Exception e) {
            // Item 2: catch global — captura erros inesperados (ex: banco fora do ar, NullPointerException)
            e.printStackTrace();
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

    private void registrarRefeicao(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId) {
        try {
            enviarMensagem(chatId, "Analisando refeição... ");
            Meal meal = mealService.registerMeal(user, text, audioBytes, userMessageId);
            String formattedText = messageFormatter.formatMealRegistered(meal);
            org.telegram.telegrambots.meta.api.objects.Message botMsg = enviarMensagemMarkdown(chatId, formattedText, criarBotoesRefeicao(meal.getId()));
            if (botMsg != null) {
                mealService.saveBotMessageId(meal.getId(), botMsg.getMessageId());
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = resolverMensagemDeErro(e);
            enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao processar e salvar a refeição.");
        }
    }

    private void registrarTreino(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId) {
        try {
            enviarMensagem(chatId, "Analisando o treino... ");
            WorkoutSession session = workoutService.registerWorkout(user, text, audioBytes, userMessageId);
            String formattedText = messageFormatter.formatWorkoutRegistered(session);
            org.telegram.telegrambots.meta.api.objects.Message botMsg = enviarMensagemMarkdown(chatId, formattedText, criarBotoesTreino(session.getId()));
            if (botMsg != null) {
                workoutService.saveBotMessageId(session.getId(), botMsg.getMessageId());
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = resolverMensagemDeErro(e);
            enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao processar e salvar o treino.");
        }
    }

    private void gerarRelatorio(UserTelegram user, String arg, long chatId) {
        try {
            java.time.LocalDate date = java.time.LocalDate.now();
            if (!arg.isEmpty()) {
                if ("ontem".equalsIgnoreCase(arg)) {
                    date = date.minusDays(1);
                } else {
                    date = parseDate(arg);
                    if (date == null) {
                        enviarMensagem(chatId, "⚠️ Formato de data inválido! Use /relatorio, /relatorio ontem, ou /relatorio DD/MM/AAAA.");
                        return;
                    }
                }
            }
            DailyReportDto report = reportService.getReportForDate(user, date);
            enviarMensagemMarkdown(chatId, messageFormatter.formatDailyReport(report, date), criarBotoesRelatorio(report));
        } catch (Exception e) {
            e.printStackTrace();
            // Item 6: uniformizar tratamento de erro com resolverMensagemDeErro
            String errMsg = resolverMensagemDeErro(e);
            enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao gerar o relatório.");
        }
    }

    private java.time.LocalDate parseDate(String input) {
        try {
            if (input.matches("\\d{2}/\\d{2}/\\d{4}")) {
                return java.time.LocalDate.parse(input, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } else if (input.matches("\\d{2}/\\d{2}")) {
                String fullDate = input + "/" + java.time.LocalDate.now().getYear();
                return java.time.LocalDate.parse(fullDate, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            // Ignora erro de parsing e retorna null
        }
        return null;
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
            e.printStackTrace();
            return null; // caller verifica null (Item 1)
        }
    }

    private void enviarMensagem(long chatId, String texto) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private org.telegram.telegrambots.meta.api.objects.Message enviarMensagemMarkdown(long chatId, String texto) {
        return enviarMensagemMarkdown(chatId, texto, null);
    }

    private org.telegram.telegrambots.meta.api.objects.Message enviarMensagemMarkdown(long chatId, String texto, org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) {
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
            e.printStackTrace();
            return null;
        }
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup criarBotoesRefeicao(Long mealId) {
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btnEdit = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        btnEdit.setText("✏️ Editar");
        btnEdit.setCallbackData("edit_meal:" + mealId);

        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btnDel = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        btnDel.setText("❌ Excluir");
        btnDel.setCallbackData("delete_meal:" + mealId);

        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = java.util.List.of(btnEdit, btnDel);
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        markup.setKeyboard(java.util.List.of(row));
        return markup;
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup criarBotoesTreino(Long workoutId) {
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btnEdit = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        btnEdit.setText("✏️ Editar");
        btnEdit.setCallbackData("edit_workout:" + workoutId);

        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btnDel = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        btnDel.setText("❌ Excluir");
        btnDel.setCallbackData("delete_workout:" + workoutId);

        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = java.util.List.of(btnEdit, btnDel);
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        markup.setKeyboard(java.util.List.of(row));
        return markup;
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup criarBotoesRelatorio(DailyReportDto report) {
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = new java.util.ArrayList<>();

        int idx = 1;
        for (Meal meal : report.getMeals()) {
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
            btn.setText("❌ Excluir Refeição " + idx);
            btn.setCallbackData("delete_meal:" + meal.getId());
            keyboard.add(java.util.List.of(btn));
            idx++;
        }

        for (WorkoutSession workout : report.getWorkouts()) {
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
            btn.setText("❌ Excluir Treino");
            btn.setCallbackData("delete_workout:" + workout.getId());
            keyboard.add(java.util.List.of(btn));
        }

        if (keyboard.isEmpty()) {
            return null;
        }

        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private void processarCallbackQuery(UserTelegram user, String data, int botMessageId, long chatId) {
        try {
            if (data.startsWith("delete_meal:")) {
                Long mealId = Long.parseLong(data.split(":")[1]);
                // Item 7: verificar existência antes de deletar — trata duplo clique graciosamente
                if (mealService.existeMeal(mealId)) {
                    mealService.deleteMeal(mealId);
                    editarMensagemBot(chatId, botMessageId, "❌ Refeição excluída com sucesso!", null);
                } else {
                    editarMensagemBot(chatId, botMessageId, "⚠️ Esta refeição já foi removida.", null);
                }
            } else if (data.startsWith("delete_workout:")) {
                Long workoutId = Long.parseLong(data.split(":")[1]);
                // Item 7: verificar existência antes de deletar — trata duplo clique graciosamente
                if (workoutService.existeWorkout(workoutId)) {
                    workoutService.deleteWorkout(workoutId);
                    editarMensagemBot(chatId, botMessageId, "❌ Treino excluído com sucesso!", null);
                } else {
                    editarMensagemBot(chatId, botMessageId, "⚠️ Este treino já foi removido.", null);
                }
            } else if (data.startsWith("edit_meal:")) {
                Long mealId = Long.parseLong(data.split(":")[1]);
                putState(chatId, "AWAITING_EDIT_MEAL:" + mealId);
                enviarMensagem(chatId, "✏️ Envie um texto ou grave um áudio com o novo conteúdo para esta refeição.");
            } else if (data.startsWith("edit_workout:")) {
                Long workoutId = Long.parseLong(data.split(":")[1]);
                putState(chatId, "AWAITING_EDIT_WORKOUT:" + workoutId);
                enviarMensagem(chatId, "✏️ Envie um texto ou grave um áudio com o novo conteúdo para este treino.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            enviarMensagem(chatId, "Ocorreu um erro ao processar sua solicitação.");
        }
    }

    private void atualizarRefeicaoEditada(Long mealId, String text, byte[] audioBytes, long chatId) {
        try {
            enviarMensagem(chatId, "Atualizando refeição... ");
            Meal meal = mealService.updateMeal(mealId, text, audioBytes);
            if (meal.getBotMessageId() != null) {
                editarMensagemBot(chatId, meal.getBotMessageId(),
                        messageFormatter.formatMealRegistered(meal),
                        criarBotoesRefeicao(meal.getId()));
                enviarMensagem(chatId, "✅ Refeição atualizada com sucesso!");
            } else {
                enviarMensagemMarkdown(chatId, messageFormatter.formatMealRegistered(meal), criarBotoesRefeicao(meal.getId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = resolverMensagemDeErro(e);
            enviarMensagem(chatId, errMsg != null ? errMsg : "Erro ao atualizar a refeição.");
        }
    }

    private void atualizarTreinoEditado(Long workoutId, String text, byte[] audioBytes, long chatId) {
        try {
            enviarMensagem(chatId, "Analisando treino... ");
            WorkoutSession session = workoutService.updateWorkout(workoutId, text, audioBytes);
            if (session.getBotMessageId() != null) {
                editarMensagemBot(chatId, session.getBotMessageId(),
                        messageFormatter.formatWorkoutRegistered(session),
                        criarBotoesTreino(session.getId()));
                enviarMensagem(chatId, "✅ Treino atualizado com sucesso!");
            } else {
                enviarMensagemMarkdown(chatId, messageFormatter.formatWorkoutRegistered(session), criarBotoesTreino(session.getId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = resolverMensagemDeErro(e);
            enviarMensagem(chatId, errMsg != null ? errMsg : "Erro ao atualizar o treino.");
        }
    }

    // Item 3: fallback para nova mensagem quando edição falha (mensagem muito antiga, deletada, etc.)
    private void editarMensagemBot(long chatId, int messageId, String novoTexto, org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) {
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
            e.printStackTrace();
            // Fallback: se não foi possível editar a mensagem original (muito antiga ou deletada), envia nova
            enviarMensagemMarkdown(chatId, novoTexto, keyboard);
        }
    }

    private void responderCallback(String callbackQueryId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Detecta erros conhecidos na cadeia de causas e retorna mensagem amigável ao usuário
    private String resolverMensagemDeErro(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null) {
                // Rate limit da API Gemini
                if (msg.contains("RESOURCE_EXHAUSTED") || msg.contains("429")) {
                    return "⏳ A IA está sobrecarregada no momento. Aguarde alguns segundos e tente novamente!";
                }
                // Item 4: entrada inválida detectada no GeminiService (texto e áudio ambos ausentes)
                if (cause instanceof IllegalArgumentException) {
                    return "⚠️ " + msg;
                }
            }
            cause = cause.getCause();
        }
        return null; // erro não mapeado, usar mensagem genérica do caller
    }
}