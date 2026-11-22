package com.bot.telegram.bot.handler;

import com.bot.telegram.bot.BotActionSender;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.WorkoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

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

    public WorkoutSession criarRascunho(UserTelegram user, String titulo, Integer userMessageId, long chatId, BotActionSender sender) {
        try {
            boolean jaExistia = workoutService.getLatestWorkoutForToday(user).isPresent();
            WorkoutSession draft = workoutService.createOrUpdateDraftWorkout(user, titulo, userMessageId);
            if (jaExistia) {
                sender.enviarMensagem(chatId, "✏️ Título do treino atualizado para '" + titulo + "'!\nPode enviar seus exercícios em texto ou áudio agora.");
            } else {
                sender.enviarMensagem(chatId, "🏋️‍♂️ Treino '" + titulo + "' iniciado!\nEnvie seus exercícios em texto ou áudio agora (ex: Puxada alta 3x12 50kg).");
            }
            return draft;
        } catch (Exception e) {
            log.error("Erro ao criar rascunho de treino para chatId={}", chatId, e);
            sender.enviarMensagem(chatId, "Ocorreu um erro ao preparar o treino.");
            return null;
        }
    }

    public WorkoutSession criarNovoTreino(UserTelegram user, String titulo, Integer userMessageId, long chatId, BotActionSender sender) {
        try {
            WorkoutSession draft = workoutService.createNewDraftWorkout(user, titulo, userMessageId);
            sender.enviarMensagem(chatId, "🏋️‍♂️ Novo treino '" + titulo + "' iniciado!\nEnvie seus exercícios em texto ou áudio agora.");
            return draft;
        } catch (Exception e) {
            log.error("Erro ao criar novo treino para chatId={}", chatId, e);
            sender.enviarMensagem(chatId, "Ocorreu um erro ao criar o novo treino.");
            return null;
        }
    }

    public void registrarExercicioOuCriarTreino(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, "Analisando exercício... ⏳");
        try {
            WorkoutService.WorkoutUpdateResult result = workoutService.addExercisesOrCreateWorkout(user, text, audioBytes, userMessageId);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            enviarCardsExercicios(result, chatId, sender);
        } catch (Exception e) {
            log.error("Erro ao registrar exercício para chatId={}", chatId, e);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao processar o exercício.");
        }
    }

    private void enviarCardsExercicios(WorkoutService.WorkoutUpdateResult result, long chatId, BotActionSender sender) {
        List<WorkoutDto.ExerciseDto> added = result.addedExercises;
        for (int i = 0; i < added.size(); i++) {
            WorkoutDto.ExerciseDto ex = added.get(i);
            List<WorkoutDto.ExerciseDto> singleList = List.of(ex);
            String autoTitle = (i == 0 && result.createdNewSession) ? result.session.getRawInput() : null;
            String formattedText = messageFormatter.formatExercisesAdded(singleList, autoTitle);
            sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesExercicios(result.session.getId(), singleList));
        }
    }

    public void atualizarExercicioEditado(Long workoutId, String exerciseId, Integer botMessageId, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, "Atualizando exercício... ⏳");
        try {
            WorkoutSession session = workoutService.editExerciseById(workoutId, exerciseId, text, audioBytes);
            List<WorkoutDto.ExerciseDto> allEx = workoutService.deserializeExercises(session.getExercisesJson());
            int idx = workoutService.findExerciseIndex(allEx, exerciseId);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            if (idx >= 0 && idx < allEx.size()) {
                List<WorkoutDto.ExerciseDto> singleList = List.of(allEx.get(idx));
                String formattedText = messageFormatter.formatExercisesAdded(singleList);
                InlineKeyboardMarkup kb = keyboardFactory.criarBotoesExercicios(workoutId, singleList);
                if (botMessageId != null) {
                    sender.editarMensagemBot(chatId, botMessageId, formattedText, kb);
                    sender.enviarMensagem(chatId, "✅ Exercício atualizado com sucesso!");
                } else {
                    sender.enviarMensagemMarkdown(chatId, formattedText, kb);
                }
            } else {
                sender.enviarMensagem(chatId, "✅ Exercício atualizado.");
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar exercício id={} do treino id={} para chatId={}", exerciseId, workoutId, chatId, e);
            if (tempMsg != null) {
                sender.deletarMensagem(chatId, tempMsg.getMessageId());
            }
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Erro ao atualizar o exercício.");
        }
    }

    public void processarExclusaoExercicio(Long workoutId, String exerciseId, int botMessageId, long chatId, BotActionSender sender) {
        try {
            workoutService.removeExerciseById(workoutId, exerciseId);
            sender.editarMensagemBot(chatId, botMessageId, "❌ Exercício excluído com sucesso\\!", null);
        } catch (IllegalArgumentException e) {
            sender.editarMensagemBot(chatId, botMessageId, "⚠️ " + messageFormatter.escapeMarkdown(e.getMessage()), null);
        } catch (Exception e) {
            sender.editarMensagemBot(chatId, botMessageId, "⚠️ Erro ao remover exercício\\.", null);
        }
    }

    public void processarExclusaoTreino(Long workoutId, int botMessageId, long chatId, BotActionSender sender) {
        if (workoutService.existeWorkout(workoutId)) {
            workoutService.deleteWorkout(workoutId);
            sender.editarMensagemBot(chatId, botMessageId, "❌ Treino excluído com sucesso\\!", null);
        } else {
            sender.editarMensagemBot(chatId, botMessageId, "⚠️ Este treino já foi removido\\.", null);
        }
    }
}
