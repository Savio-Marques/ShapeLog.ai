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

    public WorkoutSession criarRascunho(UserTelegram user, String titulo, Integer userMessageId, long chatId, BotActionSender sender) {
        try {
            boolean jaExistia = workoutService.getLatestWorkoutForToday(user).isPresent();
            WorkoutSession draft = workoutService.createOrUpdateDraftWorkout(user, titulo, userMessageId);
            if (jaExistia) {
                sender.enviarMensagem(chatId, "✏️ Título do treino atualizado para '" + titulo + "'!");
            } else {
                sender.enviarMensagem(chatId, "🏋️‍♂️ Título '" + titulo + "' salvo com sucesso!\nPara adicionar seus exercícios a este treino, utilize o comando /exercicio (ex: /exercicio puxada alta 3x12).");
            }
            return draft;
        } catch (Exception e) {
            log.error("Erro ao criar rascunho de treino para chatId={}", chatId, e);
            sender.enviarMensagem(chatId, "Ocorreu um erro ao preparar o treino.");
            return null;
        }
    }

    public void registrarExercicio(Long workoutId, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        try {
            sender.enviarMensagem(chatId, "Analisando exercício... ");
            WorkoutService.WorkoutUpdateResult result = workoutService.addExercisesToDraft(workoutId, text, audioBytes, null);
            String formattedText = messageFormatter.formatExercisesAdded(result.addedExercises);
            Message botMsg = sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesExercicios(result.session.getId(), result.startIndex, result.addedExercises.size()));
        } catch (Exception e) {
            log.error("Erro ao registrar exercício no treino id={} para chatId={}", workoutId, chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Ocorreu um erro ao processar o exercício.");
        }
    }

    public void atualizarExercicioEditado(Long workoutId, int exerciseIndex, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        try {
            sender.enviarMensagem(chatId, "Atualizando exercício... ");
            WorkoutSession session = workoutService.editExercise(workoutId, exerciseIndex, text, audioBytes);
            java.util.List<com.bot.telegram.dto.WorkoutDto.ExerciseDto> allEx = workoutService.deserializeExercises(session.getExercisesJson());
            if (exerciseIndex >= 0 && exerciseIndex < allEx.size()) {
                String formattedText = messageFormatter.formatExercisesAdded(java.util.List.of(allEx.get(exerciseIndex)));
                sender.enviarMensagemMarkdown(chatId, formattedText, keyboardFactory.criarBotoesExercicios(workoutId, exerciseIndex, 1));
            } else {
                sender.enviarMensagem(chatId, "✅ Exercício atualizado.");
            }
        } catch (Exception e) {
            log.error("Erro ao atualizar exercício index={} do treino id={} para chatId={}", exerciseIndex, workoutId, chatId, e);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagem(chatId, errMsg != null ? errMsg : "Erro ao atualizar o exercício.");
        }
    }

    public void processarExclusaoExercicio(Long workoutId, int exerciseIndex, int botMessageId, long chatId, BotActionSender sender) {
        try {
            workoutService.removeExercise(workoutId, exerciseIndex);
            sender.editarMensagemBot(chatId, botMessageId, "❌ Exercício excluído com sucesso!", null);
        } catch (Exception e) {
            sender.editarMensagemBot(chatId, botMessageId, "⚠️ Erro ao remover exercício.", null);
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
