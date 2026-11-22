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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class WorkoutHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkoutHandler.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final WorkoutService workoutService;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;
    private final ZoneId appZoneId;

    public WorkoutHandler(WorkoutService workoutService, MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory) {
        this(workoutService, messageFormatter, keyboardFactory, DEFAULT_ZONE);
    }

    @Autowired
    public WorkoutHandler(WorkoutService workoutService, MessageFormatter messageFormatter,
                          InlineKeyboardFactory keyboardFactory, ZoneId appZoneId) {
        this.workoutService = workoutService;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    public WorkoutSession criarRascunho(UserTelegram user, String titulo, Integer userMessageId, long chatId, BotActionSender sender) {
        try {
            boolean jaExistia = workoutService.getLatestWorkoutForToday(user).isPresent();
            WorkoutSession draft = workoutService.createOrUpdateDraftWorkout(user, titulo, userMessageId);
            Message sent = sender.enviarMensagemMarkdown(
                    chatId,
                    messageFormatter.formatWorkoutStarted(titulo, false, jaExistia),
                    keyboardFactory.criarBotaoSairModoTreino()
            );
            if (draft != null && sent != null) {
                draft.setBotMessageId(sent.getMessageId());
            }
            return draft;
        } catch (Exception e) {
            log.error("Erro ao criar rascunho de treino para chatId={}", chatId, e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning("Ocorreu um erro ao preparar o treino."));
            return null;
        }
    }

    public WorkoutSession criarNovoTreino(UserTelegram user, String titulo, Integer userMessageId, long chatId, BotActionSender sender) {
        try {
            WorkoutSession draft = workoutService.createNewDraftWorkout(user, titulo, userMessageId);
            Message sent = sender.enviarMensagemMarkdown(
                    chatId,
                    messageFormatter.formatWorkoutStarted(titulo, true, false),
                    keyboardFactory.criarBotaoSairModoTreino()
            );
            if (draft != null && sent != null) {
                draft.setBotMessageId(sent.getMessageId());
            }
            return draft;
        } catch (Exception e) {
            log.error("Erro ao criar novo treino para chatId={}", chatId, e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning("Ocorreu um erro ao criar o novo treino."));
            return null;
        }
    }

    public boolean registrarExercicioOuCriarTreino(UserTelegram user, String text, byte[] audioBytes, Integer userMessageId, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, messageFormatter.formatLoading("Analisando exercício"));
        try {
            WorkoutService.WorkoutUpdateResult result = workoutService.addExercisesOrCreateWorkout(user, text, audioBytes, userMessageId);
            deleteTemp(sender, chatId, tempMsg);
            enviarCardsExercicios(result, chatId, sender);
            return true;
        } catch (Exception e) {
            log.error("Erro ao registrar exercício para chatId={}", chatId, e);
            deleteTemp(sender, chatId, tempMsg);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning(
                    errMsg != null ? errMsg : "Ocorreu um erro ao processar o exercício."));
            return false;
        }
    }

    private void enviarCardsExercicios(WorkoutService.WorkoutUpdateResult result, long chatId, BotActionSender sender) {
        List<WorkoutDto.ExerciseDto> added = result.addedExercises;
        LocalDate workoutDate = toLocalDate(result.session.getCreatedAt());
        for (int i = 0; i < added.size(); i++) {
            WorkoutDto.ExerciseDto ex = added.get(i);
            List<WorkoutDto.ExerciseDto> singleList = List.of(ex);
            String autoTitle = (i == 0 && result.createdNewSession) ? result.session.getRawInput() : null;
            String formattedText = messageFormatter.formatExercisesAdded(singleList, autoTitle);
            sender.enviarMensagemMarkdown(chatId, formattedText,
                    keyboardFactory.criarBotoesExercicios(result.session.getId(), singleList, workoutDate));
        }
    }

    public boolean atualizarExercicioEditado(Long workoutId, String exerciseId, Integer botMessageId, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        Message tempMsg = sender.enviarMensagemRetornando(chatId, messageFormatter.formatLoading("Atualizando exercício"));
        try {
            WorkoutSession session = workoutService.editExerciseById(workoutId, exerciseId, text, audioBytes);
            List<WorkoutDto.ExerciseDto> allEx = workoutService.deserializeExercises(session.getExercisesJson());
            int idx = workoutService.findExerciseIndex(allEx, exerciseId);
            LocalDate workoutDate = toLocalDate(session.getCreatedAt());
            deleteTemp(sender, chatId, tempMsg);
            if (idx >= 0 && idx < allEx.size()) {
                WorkoutDto.ExerciseDto updatedEx = allEx.get(idx);
                List<WorkoutDto.ExerciseDto> singleList = List.of(updatedEx);
                InlineKeyboardMarkup kb = keyboardFactory.criarBotoesExercicios(workoutId, singleList, workoutDate);
                if (botMessageId != null) {
                    String workoutTitle = (idx == 0 && session.getRawInput() != null && !session.getRawInput().isBlank() && !session.getRawInput().startsWith("["))
                            ? session.getRawInput()
                            : null;
                    String editedCard = messageFormatter.formatExercisesAdded(singleList, workoutTitle, false, true);
                    sender.editarMensagemBot(chatId, botMessageId, editedCard, kb);
                    sender.enviarMensagemMarkdown(chatId, messageFormatter.formatExerciseUpdatedConfirmation(updatedEx),
                            keyboardFactory.criarBotaoVerRelatorio(workoutDate));
                } else {
                    sender.enviarMensagemMarkdown(chatId, messageFormatter.formatExercisesAdded(singleList), kb);
                }
            } else {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatItemUpdated("Exercício", false),
                        keyboardFactory.criarBotaoVerRelatorio(workoutDate));
            }
            return true;
        } catch (Exception e) {
            log.error("Erro ao atualizar exercício id={} do treino id={} para chatId={}", exerciseId, workoutId, chatId, e);
            deleteTemp(sender, chatId, tempMsg);
            String errMsg = sender.resolverMensagemDeErro(e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatWarning(
                    errMsg != null ? errMsg : "Erro ao atualizar o exercício."));
            return false;
        }
    }

    public void processarExclusaoExercicio(Long workoutId, String exerciseId, int botMessageId, long chatId, BotActionSender sender) {
        try {
            workoutService.removeExerciseById(workoutId, exerciseId);
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatItemDeleted("Exercício", false), null);
        } catch (IllegalArgumentException e) {
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatWarning(e.getMessage()), null);
        } catch (Exception e) {
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatWarning("Erro ao remover exercício."), null);
        }
    }

    public void processarExclusaoTreino(Long workoutId, int botMessageId, long chatId, BotActionSender sender) {
        if (workoutService.existeWorkout(workoutId)) {
            workoutService.deleteWorkout(workoutId);
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatItemDeleted("Treino", false), null);
        } else {
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatAlreadyRemoved("Este treino", false), null);
        }
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant != null ? instant.atZone(appZoneId).toLocalDate() : LocalDate.now(appZoneId);
    }

    private void deleteTemp(BotActionSender sender, long chatId, Message tempMsg) {
        if (tempMsg != null) {
            sender.deletarMensagem(chatId, tempMsg.getMessageId());
        }
    }
}
