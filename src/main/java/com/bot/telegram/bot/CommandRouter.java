package com.bot.telegram.bot;

import com.bot.telegram.bot.handler.GoalHandler;
import com.bot.telegram.bot.handler.MealHandler;
import com.bot.telegram.bot.handler.ReportHandler;
import com.bot.telegram.bot.handler.WorkoutHandler;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.IWorkoutService;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Roteia comandos, mensagens livres por estado e callbacks inline do Telegram.
 */
@Component
public class CommandRouter {

    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern PROGRAM_ONLY_PATTERN = Pattern.compile(
            "(?i)^(((stronglifts|starting\\s+strength|ppl|upper\\s+lower|full\\s*body)\\s*)?\\d+\\s*[xX×]\\s*\\d+|\\d+\\s*[hm]|(treino|programa|ficha|ciclo|semana|bloco)\\b(?!.*\\d+\\s*(kg|quilos?|reps?|repeti[cç][õo]es|km|cal|kcal)\\b).*)$"
    );
    private static final Pattern EXPLICIT_METRIC_PATTERN = Pattern.compile(
            "(?i).*(\\d+\\s*(kg|quilos?|reps?|repeti[cç][õo]es|s[eé]ries?|min|minutos?|horas?|km|metros?|rounds?|cal|kcal)\\b|\\b(amrap|emom|for\\s+time)\\b).*"
    );
    private static final Pattern EXERCISE_SETS_PATTERN = Pattern.compile(
            "(?i)^[\\p{L}\\s\\-]{2,}\\d+\\s*[xX×]\\s*\\d+.*$"
    );

    private final MealHandler mealHandler;
    private final WorkoutHandler workoutHandler;
    private final ReportHandler reportHandler;
    private final GoalHandler goalHandler;
    private final MessageFormatter messageFormatter;
    private final InlineKeyboardFactory keyboardFactory;
    private final MealService mealService;
    private final IWorkoutService workoutService;
    private final StateManager stateManager;
    private final UserService userService;
    private final ZoneId appZoneId;

    @Value("${telegram.bot.admin-id:0}")
    private Long adminId;

    public CommandRouter(MealHandler mealHandler, WorkoutHandler workoutHandler,
                         ReportHandler reportHandler, GoalHandler goalHandler,
                         MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory,
                         MealService mealService, IWorkoutService workoutService,
                         StateManager stateManager, UserService userService) {
        this(mealHandler, workoutHandler, reportHandler, goalHandler, messageFormatter, keyboardFactory,
                mealService, workoutService, stateManager, userService, DEFAULT_ZONE);
    }

    @Autowired
    public CommandRouter(MealHandler mealHandler, WorkoutHandler workoutHandler,
                         ReportHandler reportHandler, GoalHandler goalHandler,
                         MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory,
                         MealService mealService, IWorkoutService workoutService,
                         StateManager stateManager, UserService userService,
                         ZoneId appZoneId) {
        this.mealHandler = mealHandler;
        this.workoutHandler = workoutHandler;
        this.reportHandler = reportHandler;
        this.goalHandler = goalHandler;
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
        this.mealService = mealService;
        this.workoutService = workoutService;
        this.stateManager = stateManager;
        this.userService = userService;
        this.appZoneId = appZoneId != null ? appZoneId : DEFAULT_ZONE;
    }

    public void rotearComando(UserTelegram user, String text, int userMessageId, long chatId, BotActionSender sender) {
        limparEstadoAtualSeExistir(chatId, sender);
        String trimmed = text != null ? text.trim() : "";
        String[] tokens = trimmed.split("\\s+", 2);
        String comando = tokens[0].split("@")[0].toLowerCase();
        String argumento = tokens.length > 1 ? tokens[1].trim() : "";

        switch (comando) {
            case "/start" -> sender.enviarMensagemMarkdown(
                    chatId,
                    messageFormatter.formatStart(user.getFirstName()),
                    keyboardFactory.criarBotoesStart()
            );
            case "/meta" -> rotearMeta(user, argumento, chatId, sender);
            case "/refeicao" -> {
                if (argumento.isEmpty()) {
                    Message promptMsg = sender.enviarMensagemMarkdown(
                            chatId, messageFormatter.formatAwaitingMealPrompt(), keyboardFactory.criarBotaoCancelarEstado());
                    stateManager.putState(chatId, UserState.withPromptMessageId(
                            UserState.AWAITING_MEAL.serialize(), promptMsg != null ? promptMsg.getMessageId() : null));
                } else {
                    mealHandler.registrarRefeicao(user, argumento, null, userMessageId, chatId, sender);
                }
            }
            case "/treino" -> {
                if (argumento.isEmpty()) {
                    Message promptMsg = sender.enviarMensagemMarkdown(
                            chatId, messageFormatter.formatAwaitingWorkoutTitlePrompt(), keyboardFactory.criarBotaoCancelarEstado());
                    stateManager.putState(chatId, UserState.withPromptMessageId(
                            UserState.AWAITING_WORKOUT_TITLE.serialize(), promptMsg != null ? promptMsg.getMessageId() : null));
                } else if (looksLikeExerciseInput(argumento)) {
                    workoutHandler.registrarExercicioOuCriarTreino(user, argumento, null, userMessageId, chatId, sender);
                } else {
                    iniciarOuConfirmarTituloTreino(user, argumento, userMessageId, chatId, sender);
                }
            }
            case "/exercicio" -> {
                if (argumento.isEmpty()) {
                    Message promptMsg = sender.enviarMensagemMarkdown(
                            chatId, messageFormatter.formatAwaitingExercisePrompt(), keyboardFactory.criarBotaoCancelarEstado());
                    stateManager.putState(chatId, UserState.withPromptMessageId(
                            UserState.AWAITING_EXERCISE.serialize(), promptMsg != null ? promptMsg.getMessageId() : null));
                } else {
                    workoutHandler.registrarExercicioOuCriarTreino(user, argumento, null, userMessageId, chatId, sender);
                }
            }
            case "/relatorio" -> reportHandler.gerarRelatorio(user, argumento, chatId, sender);
            case "/aprovar" -> rotearAprovar(chatId, trimmed, sender);
            case "/revogar" -> rotearRevogar(chatId, trimmed, sender);
            case "/usuarios" -> rotearListarUsuarios(chatId, sender);
            default -> sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUnknownCommand());
        }
    }

    public void rotearEstadoTexto(UserTelegram user, String text, int userMessageId, long chatId, BotActionSender sender) {
        String rawState = stateManager.getState(chatId);
        UserState state = UserState.from(rawState);
        if (state == null) {
            String token = stateManager.putPendingInput(chatId, text, null, userMessageId);
            sender.enviarMensagemMarkdown(
                    chatId,
                    messageFormatter.formatPendingInputPrompt(text, false),
                    keyboardFactory.criarBotoesConfirmacaoEntrada(token)
            );
            return;
        }
        if (state == UserState.AWAITING_WORKOUT_TITLE) {
            limparEstadoEPrompt(chatId, rawState, sender);
            iniciarOuConfirmarTituloTreino(user, text, userMessageId, chatId, sender);
            return;
        }
        boolean handled = switch (state) {
            case AWAITING_MEAL -> mealHandler.registrarRefeicao(user, text, null, userMessageId, chatId, sender);
            case AWAITING_EXERCISE -> workoutHandler.registrarExercicioOuCriarTreino(user, text, null, userMessageId, chatId, sender);
            case AWAITING_EDIT_MEAL -> mealHandler.atualizarRefeicaoEditada(user, UserState.extractId(rawState), text, null, chatId, sender);
            case AWAITING_EDIT_EXERCISE -> processarEdicaoExercicio(rawState, text, null, chatId, sender);
            case AWAITING_GOAL -> processarEntradaMeta(user, text, chatId, sender);
            default -> {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUnknownState());
                yield true;
            }
        };
        if (handled) {
            limparEstadoEPrompt(chatId, rawState, sender);
        }
    }

    public void rotearEstadoVoz(UserTelegram user, byte[] audioBytes, long chatId, BotActionSender sender) {
        String rawState = stateManager.getState(chatId);
        UserState state = UserState.from(rawState);
        if (state == null) {
            String token = stateManager.putPendingInput(chatId, null, audioBytes, null);
            sender.enviarMensagemMarkdown(
                    chatId,
                    messageFormatter.formatPendingInputPrompt(null, true),
                    keyboardFactory.criarBotoesConfirmacaoEntrada(token)
            );
            return;
        }
        if (state == UserState.AWAITING_WORKOUT_TITLE) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatVoiceWorkoutTitleRequired());
            return;
        }
        boolean handled = switch (state) {
            case AWAITING_MEAL -> mealHandler.registrarRefeicao(user, null, audioBytes, null, chatId, sender);
            case AWAITING_EXERCISE -> workoutHandler.registrarExercicioOuCriarTreino(user, null, audioBytes, null, chatId, sender);
            case AWAITING_EDIT_MEAL -> mealHandler.atualizarRefeicaoEditada(user, UserState.extractId(rawState), null, audioBytes, chatId, sender);
            case AWAITING_EDIT_EXERCISE -> processarEdicaoExercicio(rawState, null, audioBytes, chatId, sender);
            case AWAITING_GOAL -> goalHandler.sugerirMetasPorIA(user, null, audioBytes, chatId, sender);
            default -> {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUnknownState());
                yield true;
            }
        };
        if (handled) {
            limparEstadoEPrompt(chatId, rawState, sender);
        }
    }

    public void rotearCallback(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        try {
            if (data.startsWith("pending_") || data.startsWith("wk_") || "cancel_state".equals(data)) {
                rotearCallbackPendenteOuEstado(user, data, botMessageId, chatId, sender);
            } else if ("open_goals".equals(data) || "cancel_goal".equals(data) || data.startsWith("confirm_goal:")
                    || data.startsWith("open_report:") || data.startsWith("rep_")) {
                rotearCallbackMetaOuRelatorio(user, data, botMessageId, chatId, sender);
            } else if (data.startsWith("delete_meal:") || data.startsWith("cfm_del_meal:") || data.startsWith("cnl_del_meal:")
                    || data.startsWith("edit_meal:") || data.startsWith("ask_del_rep_m:") || data.startsWith("del_rep_m:")) {
                rotearCallbackRefeicao(user, data, botMessageId, chatId, sender);
            } else if (data.startsWith("delete_workout:") || data.startsWith("edit_ex:")
                    || data.startsWith("delete_ex:") || data.startsWith("cfm_del_ex:") || data.startsWith("cnl_del_ex:")
                    || data.startsWith("ask_del_rep_w:") || data.startsWith("del_rep_w:")) {
                rotearCallbackTreino(user, data, botMessageId, chatId, sender);
            }
        } catch (Exception e) {
            log.error("Erro ao processar callback data='{}' para chatId={}", data, chatId, e);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUnexpectedError());
        }
    }

    private void rotearCallbackRefeicao(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        Long mealId = Long.parseLong(data.split(":")[1]);
        Meal meal = mealService.findByIdAndUserId(mealId, chatId).orElse(null);
        if (meal == null) {
            if (!mealService.existeMeal(mealId)) {
                sender.responderToast("Esta refeição já foi removida");
                sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatAlreadyRemoved("Esta refeição", true), null);
            } else {
                log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} mealId={}", chatId, mealId);
            }
            return;
        }
        LocalDate mealDate = toLocalDate(meal.getCreatedAt());
        if (data.startsWith("edit_meal:")) {
            limparEstadoAtualSeExistir(chatId, sender);
            sender.responderToast("Envie a correção");
            Message promptMsg = sender.enviarMensagemMarkdown(
                    chatId,
                    messageFormatter.formatEditMealPrompt(meal.getDescription()),
                    keyboardFactory.criarBotaoCancelarEstado()
            );
            stateManager.putState(chatId, UserState.withPromptMessageId(
                    UserState.AWAITING_EDIT_MEAL.withId(mealId), promptMsg != null ? promptMsg.getMessageId() : null));
        } else if (data.startsWith("delete_meal:")) {
            sender.editarTecladoBot(chatId, botMessageId, keyboardFactory.criarBotoesConfirmarExclusaoRefeicao(mealId));
            sender.responderToast("Confirme a exclusão abaixo");
        } else if (data.startsWith("cfm_del_meal:")) {
            sender.responderToast("Refeição excluída");
            mealHandler.processarExclusaoRefeicao(mealId, botMessageId, chatId, sender);
        } else if (data.startsWith("cnl_del_meal:")) {
            sender.editarTecladoBot(chatId, botMessageId, keyboardFactory.criarBotoesRefeicao(mealId, mealDate));
            sender.responderToast("Exclusão cancelada");
        } else if (data.startsWith("ask_del_rep_m:")) {
            sender.editarTecladoBot(chatId, botMessageId,
                    keyboardFactory.criarBotoesConfirmarExclusaoRelatorio("del_rep_m:" + mealId, mealDate));
            sender.responderToast("Confirme a exclusão abaixo");
        } else if (data.startsWith("del_rep_m:")) {
            mealService.deleteMeal(mealId);
            sender.responderToast("Refeição excluída");
            reportHandler.atualizarRelatorioAposExclusao(user, mealDate, botMessageId, chatId, sender);
        }
    }

    private void rotearCallbackTreino(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        if (data.startsWith("delete_workout:") || data.startsWith("ask_del_rep_w:") || data.startsWith("del_rep_w:")) {
            Long workoutId = Long.parseLong(data.split(":")[1]);
            WorkoutSession ws = findOwnedWorkoutOrNotifyRemoved(workoutId, chatId, botMessageId, sender);
            if (ws == null) {
                return;
            }
            LocalDate workoutDate = toLocalDate(ws.getCreatedAt());
            if (data.startsWith("delete_workout:")) {
                sender.responderToast("Treino excluído");
                workoutHandler.processarExclusaoTreino(workoutId, botMessageId, chatId, sender);
            } else if (data.startsWith("ask_del_rep_w:")) {
                sender.editarTecladoBot(chatId, botMessageId,
                        keyboardFactory.criarBotoesConfirmarExclusaoRelatorio("del_rep_w:" + workoutId, workoutDate));
                sender.responderToast("Confirme a exclusão abaixo");
            } else {
                workoutService.deleteWorkout(workoutId);
                sender.responderToast("Treino excluído");
                reportHandler.atualizarRelatorioAposExclusao(user, workoutDate, botMessageId, chatId, sender);
            }
            return;
        }

        String[] parts = data.split(":");
        if (parts.length < 3) {
            log.error("Callback de exercício malformado: '{}'", data);
            return;
        }
        Long workoutId = Long.parseLong(parts[1]);
        String exerciseRef = parts[2];
        WorkoutSession ws = findOwnedWorkoutOrNotifyRemoved(workoutId, chatId, botMessageId, sender);
        if (ws == null) {
            return;
        }

        List<WorkoutDto.ExerciseDto> allEx = workoutService.deserializeExercises(ws.getExercisesJson());
        int idx = workoutService.findExerciseIndex(allEx, exerciseRef);
        boolean enforceExerciseExists = !data.startsWith("cfm_del_ex:") && allEx != null && !allEx.isEmpty();
        if (enforceExerciseExists && idx < 0) {
            sender.responderToast("Este exercício já foi removido");
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatAlreadyRemoved("Este exercício", false), null);
            return;
        }

        if (data.startsWith("edit_ex:")) {
            limparEstadoAtualSeExistir(chatId, sender);
            String summary = (idx >= 0 && idx < allEx.size()) ? messageFormatter.formatExerciseSummary(allEx.get(idx)) : null;
            sender.responderToast("Envie a correção");
            Message promptMsg = sender.enviarMensagemMarkdown(
                    chatId,
                    messageFormatter.formatEditExercisePrompt(summary),
                    keyboardFactory.criarBotaoCancelarEstado()
            );
            stateManager.putState(chatId, UserState.withPromptMessageId(
                    UserState.AWAITING_EDIT_EXERCISE.withPayload(workoutId + ":" + exerciseRef + ":" + botMessageId),
                    promptMsg != null ? promptMsg.getMessageId() : null
            ));
        } else if (data.startsWith("delete_ex:")) {
            sender.editarTecladoBot(chatId, botMessageId, keyboardFactory.criarBotoesConfirmarExclusaoExercicio(workoutId, exerciseRef));
            sender.responderToast("Confirme a exclusão abaixo");
        } else if (data.startsWith("cfm_del_ex:")) {
            sender.responderToast("Exercício excluído");
            workoutHandler.processarExclusaoExercicio(workoutId, exerciseRef, botMessageId, chatId, sender);
        } else if (data.startsWith("cnl_del_ex:")) {
            LocalDate workoutDate = toLocalDate(ws.getCreatedAt());
            WorkoutDto.ExerciseDto restored = (idx >= 0 && idx < allEx.size())
                    ? allEx.get(idx)
                    : WorkoutDto.ExerciseDto.builder().id(exerciseRef).build();
            sender.editarTecladoBot(chatId, botMessageId,
                    keyboardFactory.criarBotoesExercicios(workoutId, List.of(restored), workoutDate));
            sender.responderToast("Exclusão cancelada");
        }
    }

    private void rotearCallbackPendenteOuEstado(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        if ("cancel_state".equals(data)) {
            String existing = stateManager.getState(chatId);
            if (existing == null) {
                sender.editarTecladoBot(chatId, botMessageId, null);
                sender.responderToast("Operação já encerrada");
                return;
            }
            stateManager.removeState(chatId);
            UserState st = UserState.from(existing);
            if (st == UserState.AWAITING_GOAL) {
                sender.responderToast("Edição de metas cancelada");
                sender.editarTecladoBot(chatId, botMessageId, null);
            } else if (st == UserState.AWAITING_EXERCISE) {
                sender.responderToast("Saiu do modo treino");
                sender.editarTecladoBot(chatId, botMessageId, null);
            } else {
                sender.responderToast("Operação cancelada");
                sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatActionCancelled(), null);
            }
            return;
        }
        if (data.startsWith("pending_meal:")) {
            PendingInput pending = consumeOrNotifyExpired(chatId, botMessageId, data.substring("pending_meal:".length()),
                    "Envie sua refeição novamente.", sender);
            if (pending == null) return;
            sender.responderToast("⏳ Analisando...");
            sender.deletarMensagem(chatId, botMessageId);
            mealHandler.registrarRefeicao(user, pending.text(), pending.audioBytes(), pending.userMessageId(), chatId, sender);
        } else if (data.startsWith("pending_exercise:")) {
            PendingInput pending = consumeOrNotifyExpired(chatId, botMessageId, data.substring("pending_exercise:".length()),
                    "Envie seu exercício novamente.", sender);
            if (pending == null) return;
            sender.responderToast("⏳ Analisando...");
            sender.deletarMensagem(chatId, botMessageId);
            workoutHandler.registrarExercicioOuCriarTreino(user, pending.text(), pending.audioBytes(), pending.userMessageId(), chatId, sender);
        } else if (data.startsWith("pending_cancel:")) {
            stateManager.consumePendingInput(chatId, data.substring("pending_cancel:".length()));
            sender.responderToast("Operação cancelada");
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatActionCancelled(), null);
        } else if (data.startsWith("wk_rename:")) {
            PendingInput pending = consumeOrNotifyExpired(chatId, botMessageId, data.substring("wk_rename:".length()),
                    "Envie /treino novamente.", sender);
            if (pending == null) return;
            sender.responderToast("Treino renomeado");
            sender.deletarMensagem(chatId, botMessageId);
            WorkoutSession draft = workoutHandler.criarRascunho(user, pending.text(), pending.userMessageId(), chatId, sender);
            stateManager.putState(chatId, UserState.withPromptMessageId(
                    UserState.AWAITING_EXERCISE.serialize(), draft != null ? draft.getBotMessageId() : null));
        } else if (data.startsWith("wk_new:")) {
            PendingInput pending = consumeOrNotifyExpired(chatId, botMessageId, data.substring("wk_new:".length()),
                    "Envie /treino novamente.", sender);
            if (pending == null) return;
            sender.responderToast("Novo treino criado");
            sender.deletarMensagem(chatId, botMessageId);
            WorkoutSession draft = workoutHandler.criarNovoTreino(user, pending.text(), pending.userMessageId(), chatId, sender);
            stateManager.putState(chatId, UserState.withPromptMessageId(
                    UserState.AWAITING_EXERCISE.serialize(), draft != null ? draft.getBotMessageId() : null));
        }
    }

    private void rotearCallbackMetaOuRelatorio(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        if ("open_goals".equals(data)) {
            limparEstadoAtualSeExistir(chatId, sender);
            sender.responderToast("Suas metas");
            Message promptMsg = goalHandler.mostrarMetasAtuais(user, chatId, sender);
            stateManager.putState(chatId, UserState.withPromptMessageId(
                    UserState.AWAITING_GOAL.serialize(), promptMsg != null ? promptMsg.getMessageId() : null));
        } else if ("cancel_goal".equals(data)) {
            sender.responderToast("Sugestão cancelada");
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatActionCancelled(), null);
        } else if (data.startsWith("confirm_goal:")) {
            String[] parts = data.split(":");
            if (parts.length < 5) {
                log.error("Callback confirm_goal malformado: '{}'", data);
                return;
            }
            int cal = Integer.parseInt(parts[1]);
            int prot = Integer.parseInt(parts[2]);
            int carb = Integer.parseInt(parts[3]);
            int fat = Integer.parseInt(parts[4]);
            limparEstadoAtualSeExistir(chatId, sender);
            goalHandler.confirmarMetasSugeridas(user, cal, prot, carb, fat, botMessageId, chatId, sender);
        } else if (data.startsWith("open_report:")) {
            String raw = data.substring("open_report:".length());
            LocalDate date = "today".equals(raw) ? LocalDate.now(appZoneId) : LocalDate.parse(raw);
            sender.responderToast("Abrindo relatório");
            reportHandler.abrirRelatorioPorData(user, date, chatId, sender);
        } else if (data.startsWith("rep_nav:")) {
            LocalDate date = LocalDate.parse(data.substring("rep_nav:".length()));
            sender.responderToast("Relatório atualizado");
            reportHandler.navegarRelatorio(user, date, botMessageId, chatId, sender);
        } else if (data.startsWith("rep_manage:")) {
            LocalDate date = LocalDate.parse(data.substring("rep_manage:".length()));
            sender.responderToast("Selecione o item");
            reportHandler.abrirModoGerenciar(user, date, botMessageId, chatId, sender);
        } else if (data.startsWith("rep_view:")) {
            LocalDate date = LocalDate.parse(data.substring("rep_view:".length()));
            sender.responderToast("Relatório atualizado");
            reportHandler.voltarModoVisualizacao(user, date, botMessageId, chatId, sender);
        }
    }

    private void limparEstadoAtualSeExistir(long chatId, BotActionSender sender) {
        String existing = stateManager.getState(chatId);
        if (existing != null) {
            limparEstadoEPrompt(chatId, existing, sender);
        }
    }

    private void limparEstadoEPrompt(long chatId, String rawState, BotActionSender sender) {
        stateManager.removeState(chatId);
        UserState st = UserState.from(rawState);
        Integer promptMsgId = UserState.extractPromptMessageId(rawState);
        if (st != null && promptMsgId != null) {
            if (st == UserState.AWAITING_GOAL || st == UserState.AWAITING_EXERCISE) {
                sender.editarTecladoBot(chatId, promptMsgId, null);
            } else {
                sender.deletarMensagem(chatId, promptMsgId);
            }
        }
    }

    private PendingInput consumeOrNotifyExpired(long chatId, int botMessageId, String token, String retryHint, BotActionSender sender) {
        PendingInput pending = stateManager.consumePendingInput(chatId, token);
        if (pending == null) {
            sender.responderToast("Solicitação expirada");
            sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatInputExpired(retryHint), null);
        }
        return pending;
    }

    private WorkoutSession findOwnedWorkoutOrNotifyRemoved(Long workoutId, long chatId, int botMessageId, BotActionSender sender) {
        WorkoutSession ws = workoutService.findByIdAndUserId(workoutId, chatId).orElse(null);
        if (ws == null) {
            if (!workoutService.existeWorkout(workoutId)) {
                sender.responderToast("Este treino já foi removido");
                sender.editarMensagemBot(chatId, botMessageId, messageFormatter.formatAlreadyRemoved("Este treino", false), null);
            } else {
                log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} workoutId={}", chatId, workoutId);
            }
        }
        return ws;
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant != null ? instant.atZone(appZoneId).toLocalDate() : LocalDate.now(appZoneId);
    }

    private void iniciarOuConfirmarTituloTreino(UserTelegram user, String titulo, Integer userMessageId, long chatId, BotActionSender sender) {
        Optional<WorkoutSession> optExisting = workoutService.getLatestWorkoutForToday(user);
        if (optExisting.isPresent()) {
            WorkoutSession existing = optExisting.get();
            List<WorkoutDto.ExerciseDto> existingExercises = workoutService.deserializeExercises(existing.getExercisesJson());
            if (existingExercises != null && !existingExercises.isEmpty()) {
                String existingTitle = (existing.getRawInput() != null && !existing.getRawInput().isBlank())
                        ? existing.getRawInput()
                        : "Treino";
                String token = stateManager.putPendingInput(chatId, titulo, null, userMessageId);
                sender.enviarMensagemMarkdown(
                        chatId,
                        messageFormatter.formatWorkoutConflictPrompt(existingTitle, titulo),
                        keyboardFactory.criarBotoesConflitoTreino(token)
                );
                return;
            }
        }
        WorkoutSession draft = workoutHandler.criarRascunho(user, titulo, userMessageId, chatId, sender);
        stateManager.putState(chatId, UserState.withPromptMessageId(
                UserState.AWAITING_EXERCISE.serialize(), draft != null ? draft.getBotMessageId() : null));
    }

    private boolean looksLikeExerciseInput(String input) {
        if (input == null) {
            return false;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty() || PROGRAM_ONLY_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        return EXPLICIT_METRIC_PATTERN.matcher(trimmed).matches()
                || EXERCISE_SETS_PATTERN.matcher(trimmed).matches();
    }

    private boolean processarEdicaoExercicio(String rawState, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        String payload = UserState.extractPayload(rawState);
        if (payload != null && payload.contains(":")) {
            String[] parts = payload.split(":");
            Long workoutId = Long.parseLong(parts[0]);
            String exerciseId = parts[1];
            Integer originalBotMsgId = parts.length >= 3 ? Integer.parseInt(parts[2]) : null;
            return workoutHandler.atualizarExercicioEditado(workoutId, exerciseId, originalBotMsgId, text, audioBytes, chatId, sender);
        }
        return true;
    }

    private void rotearMeta(UserTelegram user, String argumento, long chatId, BotActionSender sender) {
        if (argumento == null || argumento.isBlank()) {
            Message promptMsg = goalHandler.mostrarMetasAtuais(user, chatId, sender);
            stateManager.putState(chatId, UserState.withPromptMessageId(
                    UserState.AWAITING_GOAL.serialize(), promptMsg != null ? promptMsg.getMessageId() : null));
            return;
        }
        processarEntradaMeta(user, argumento, chatId, sender);
    }

    private boolean processarEntradaMeta(UserTelegram user, String input, long chatId, BotActionSender sender) {
        String trimmed = input != null ? input.trim() : "";
        if (trimmed.isEmpty()) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatInvalidGoalMissingInput());
            return false;
        }
        String[] partes = trimmed.split("\\s+");
        boolean allNumbers = Arrays.stream(partes).allMatch(p -> p.matches("-?\\d+([.,]\\d+)?"));
        if (allNumbers) {
            if (partes.length != 4) {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatInvalidGoalFormat());
                return false;
            }
            try {
                int cal  = (int) Math.round(Double.parseDouble(partes[0].replace(',', '.')));
                int prot = (int) Math.round(Double.parseDouble(partes[1].replace(',', '.')));
                int carb = (int) Math.round(Double.parseDouble(partes[2].replace(',', '.')));
                int fat  = (int) Math.round(Double.parseDouble(partes[3].replace(',', '.')));
                if (cal <= 0 || prot <= 0 || carb <= 0 || fat <= 0) {
                    sender.enviarMensagemMarkdown(chatId, messageFormatter.formatInvalidGoalValues());
                    return false;
                }
                return goalHandler.salvarMetasDiretas(user, cal, prot, carb, fat, chatId, sender);
            } catch (NumberFormatException e) {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatInvalidGoalValues());
                return false;
            }
        }
        return goalHandler.sugerirMetasPorIA(user, trimmed, null, chatId, sender);
    }

    private void rotearAprovar(long chatId, String text, BotActionSender sender) {
        if (!isAdmin(chatId)) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUnknownCommand());
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatAdminUsageError("/aprovar <ID_DO_USUARIO>"));
            return;
        }
        try {
            Long targetId = Long.parseLong(parts[1]);
            Optional<UserTelegram> optUser = userService.approveUser(targetId);
            if (optUser.isPresent()) {
                UserTelegram u = optUser.get();
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUserApprovedForAdmin(u.getFirstName(), u.getId()));
                sender.enviarMensagemMarkdown(targetId, messageFormatter.formatUserApprovedForUser());
            } else {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUserNotFound());
            }
        } catch (NumberFormatException e) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatInvalidNumericId());
        }
    }

    private void rotearRevogar(long chatId, String text, BotActionSender sender) {
        if (!isAdmin(chatId)) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUnknownCommand());
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatAdminUsageError("/revogar <ID_DO_USUARIO>"));
            return;
        }
        try {
            Long targetId = Long.parseLong(parts[1]);
            Optional<UserTelegram> optUser = userService.revokeUser(targetId);
            if (optUser.isPresent()) {
                UserTelegram u = optUser.get();
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUserRevokedForAdmin(u.getFirstName(), u.getId()));
            } else {
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUserNotFound());
            }
        } catch (NumberFormatException e) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatInvalidNumericId());
        }
    }

    private void rotearListarUsuarios(long chatId, BotActionSender sender) {
        if (!isAdmin(chatId)) {
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatUnknownCommand());
            return;
        }
        sender.enviarMensagemMarkdown(chatId, messageFormatter.formatApprovedUsersList(userService.listApprovedUsers()));
    }

    private boolean isAdmin(long chatId) {
        return adminId != null && adminId > 0 && adminId.equals(chatId);
    }
}
