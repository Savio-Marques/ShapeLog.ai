package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.WorkoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
public class WorkoutHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkoutHandler.class);

    private final WorkoutService workoutService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;

    public WorkoutHandler(WorkoutService workoutService, MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this.workoutService = workoutService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
    }

    public void registrarTreino(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId, BotActionSender sender) {
        try {
            sender.enviarMensagem(chatId, "Analisando o treino... ");
            WorkoutSession session = workoutService.registerWorkout(user, text, audioBytes, userMessageId);
            String formattedText = messageFormatter.formatWorkoutRegistered(session);
            Message botMsg = sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesTreino(session.getId()));
            if (botMsg != null) {
                workoutService.saveBotMessageId(session.getId(), botMsg.getMessageId());
            }
        } catch (Exception e) {
            log.error("Erro ao registrar treino para chatId={}", chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao processar e salvar o treino.");
        }
    }

    public void atualizarTreinoEditado(Long workoutId, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        try {
            sender.enviarMensagem(chatId, "Analisando treino... ");
            WorkoutSession session = workoutService.updateWorkout(workoutId, text, audioBytes);
            if (session.getBotMessageId() != null) {
                sender.editarMensagemBot(chatId, session.getBotMessageId(),
                        messageFormatter.formatWorkoutRegistered(session),
                        keyboardFactory.criarBotoesTreino(session.getId()));
                sender.enviarMensagem(chatId, "✅ Treino atualizado com sucesso!");
            } else {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWorkoutRegistered(session), keyboardFactory.criarBotoesTreino(session.getId()));
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar treino id={} para chatId={}", workoutId, chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Erro ao atualizar o treino.");
        }
    }

    public void processarExclusaoTreino(Long workoutId, int botMessageId, long chatId, BotActionSender sender) {
        if (workoutService.existeWorkout(workoutId)) {
            workoutService.deleteWorkout(workoutId);
            sender.editarMensagemBot(chatId, botMessageId, "❌ Treino excluído com sucesso!", null);
        } else {
            sender.editarMensagemBot(chatId, botMessageId, "⚠️ Este treino já foi removido.", null);
        }
    }
}
