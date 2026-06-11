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
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.GetFile;
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

    private final Map<Long, String> userStates = new ConcurrentHashMap<>();

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();

            // Obter ou registrar usuário no banco
            UserTelegram user = userService.getOrCreateUser(
                    chatId,
                    update.getMessage().getFrom().getUserName(),
                    update.getMessage().getFrom().getFirstName()
            );

            if (update.getMessage().hasText()) {
                String text = update.getMessage().getText().trim();
                
                // Se for um comando, limpa o estado anterior e o processa
                if (text.startsWith("/")) {
                    userStates.remove(chatId);
                    
                    if (text.startsWith("/start")) {
                        enviarMensagemMarkdown(chatId, messageFormatter.formatStart(user.getFirstName()));
                    } else if (text.startsWith("/meta")) {
                        configurarMeta(user, text, chatId);
                    } else if (text.startsWith("/refeicao")) {
                        String descricao = text.replace("/refeicao", "").trim();
                        if (descricao.isEmpty()) {
                            userStates.put(chatId, "AWAITING_MEAL");
                            enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto sua refeição agora!");
                        } else {
                            registrarRefeicao(user, descricao, null, chatId);
                        }
                    } else if (text.startsWith("/treino")) {
                        String descricao = text.replace("/treino", "").trim();
                        if (descricao.isEmpty()) {
                            userStates.put(chatId, "AWAITING_WORKOUT");
                            enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto seu treino agora!");
                        } else {
                            registrarTreino(user, descricao, null, chatId);
                        }
                    } else if (text.startsWith("/relatorio")) {
                        String arg = text.replace("/relatorio", "").trim();
                        gerarRelatorio(user, arg, chatId);
                    } else {
                        enviarMensagem(chatId, "Comando não reconhecido. Use /refeicao, /treino, /meta ou /relatorio.");
                    }
                } else {
                    // Não é um comando. Verifica se o usuário estava no fluxo de aguardar áudio/texto
                    String state = userStates.remove(chatId);
                    if ("AWAITING_MEAL".equals(state)) {
                        registrarRefeicao(user, text, null, chatId);
                    } else if ("AWAITING_WORKOUT".equals(state)) {
                        registrarTreino(user, text, null, chatId);
                    } else {
                        enviarMensagem(chatId, "Por favor, envie primeiro o comando /refeicao ou /treino antes de descrever os alimentos ou exercícios.");
                    }
                }
            } else if (update.getMessage().hasVoice()) {
                Voice voice = update.getMessage().getVoice();
                
                // Verifica o estado atual para saber o que fazer com a mensagem de voz
                String state = userStates.remove(chatId);
                if ("AWAITING_MEAL".equals(state)) {
                    byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                    registrarRefeicao(user, null, audioBytes, chatId);
                } else if ("AWAITING_WORKOUT".equals(state)) {
                    byte[] audioBytes = obterBytesDoAudio(voice.getFileId());
                    registrarTreino(user, null, audioBytes, chatId);
                } else {
                    enviarMensagem(chatId, "Por favor, primeiro envie o comando correspondente (/refeicao ou /treino) e em seguida grave o áudio.");
                }
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

    private void registrarRefeicao(UserTelegram user, String text, byte[] audioBytes, long chatId) {
        try {
            enviarMensagem(chatId, "Analisando refeição... ");
            Meal meal = mealService.registerMeal(user, text, audioBytes);
            enviarMensagemMarkdown(chatId, messageFormatter.formatMealRegistered(meal));
        } catch (Exception e) {
            e.printStackTrace();
            enviarMensagem(chatId, "Ocorreu um erro ao processar e salvar a refeição.");
        }
    }

    private void registrarTreino(UserTelegram user, String text, byte[] audioBytes, long chatId) {
        try {
            enviarMensagem(chatId, "Analisando o treino... ");
            WorkoutSession session = workoutService.registerWorkout(user, text, audioBytes);
            enviarMensagemMarkdown(chatId, messageFormatter.formatWorkoutRegistered(session));
        } catch (Exception e) {
            e.printStackTrace();
            enviarMensagem(chatId, "Ocorreu um erro ao processar e salvar o treino.");
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
            enviarMensagemMarkdown(chatId, messageFormatter.formatDailyReport(report, date));
        } catch (Exception e) {
            e.printStackTrace();
            enviarMensagem(chatId, "Ocorreu um erro ao gerar o relatório.");
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
            return null;
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

    private void enviarMensagemMarkdown(long chatId, String texto) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(texto);
        message.setParseMode("Markdown");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}