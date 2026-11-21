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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class CommandRouter {

    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);
    private static final Pattern EXERCISE_INPUT_PATTERN = Pattern.compile(
            "(?i).*(\\d+\\s*[xX×]\\s*\\d+|\\d+\\s*(kg|quilos?|reps?|repeti[cç][õo]es|s[eé]ries?|min|minutos?|h|horas?|km|m\\b|metros?|rounds?|cal|kcal)|\\b(amrap|emom|for\\s+time)\\b).*"
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

    @Value("${telegram.bot.admin-id:0}")
    private Long adminId;

    public CommandRouter(MealHandler mealHandler, WorkoutHandler workoutHandler,
                         ReportHandler reportHandler, GoalHandler goalHandler,
                         MessageFormatter messageFormatter, InlineKeyboardFactory keyboardFactory,
                         MealService mealService, IWorkoutService workoutService,
                         StateManager stateManager, UserService userService) {
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
    }

    public void rotearComando(UserTelegram user, String text, int userMessageId, long chatId, BotActionSender sender) {
        stateManager.removeState(chatId);
        String trimmed = text != null ? text.trim() : "";
        String[] tokens = trimmed.split("\\s+", 2);
        String comando = tokens[0].split("@")[0].toLowerCase();
        String argumento = tokens.length > 1 ? tokens[1].trim() : "";

        switch (comando) {
            case "/start" ->
                sender.enviarMensagemMarkdown(chatId, messageFormatter.formatStart(user.getFirstName()));
            case "/meta" ->
                rotearMeta(user, argumento, chatId, sender);
            case "/refeicao" -> {
                if (argumento.isEmpty()) {
                    stateManager.putState(chatId, UserState.AWAITING_MEAL.serialize());
                    sender.enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto sua refeição agora!");
                } else {
                    mealHandler.registrarRefeicao(user, argumento, null, userMessageId, chatId, sender);
                }
            }
            case "/treino" -> {
                if (argumento.isEmpty()) {
                    stateManager.putState(chatId, UserState.AWAITING_WORKOUT_TITLE.serialize());
                    sender.enviarMensagem(chatId, "💪 Qual o título do seu treino hoje? (Ex: Costas e Bíceps, Treino A)");
                } else if (looksLikeExerciseInput(argumento)) {
                    workoutHandler.registrarExercicioOuCriarTreino(user, argumento, null, userMessageId, chatId, sender);
                } else {
                    iniciarOuConfirmarTituloTreino(user, argumento, userMessageId, chatId, sender);
                }
            }
            case "/exercicio" -> {
                if (argumento.isEmpty()) {
                    stateManager.putState(chatId, UserState.AWAITING_EXERCISE.serialize());
                    sender.enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto o exercício agora!");
                } else {
                    workoutHandler.registrarExercicioOuCriarTreino(user, argumento, null, userMessageId, chatId, sender);
                }
            }
            case "/relatorio" ->
                reportHandler.gerarRelatorio(user, argumento, chatId, sender);
            case "/aprovar" ->
                rotearAprovar(chatId, trimmed, sender);
            case "/revogar" ->
                rotearRevogar(chatId, trimmed, sender);
            case "/usuarios" ->
                rotearListarUsuarios(chatId, sender);
            default ->
                sender.enviarMensagem(chatId, "Comando não reconhecido. Use /refeicao, /treino, /exercicio, /meta ou /relatorio.");
        }
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
        workoutHandler.criarRascunho(user, titulo, userMessageId, chatId, sender);
        stateManager.putState(chatId, UserState.AWAITING_EXERCISE.serialize());
    }

    private boolean looksLikeExerciseInput(String input) {
        return input != null && EXERCISE_INPUT_PATTERN.matcher(input).matches();
    }

    private void rotearAprovar(long chatId, String text, BotActionSender sender) {
        if (!isAdmin(chatId)) {
            sender.enviarMensagem(chatId, "Comando não reconhecido. Use /refeicao, /treino, /exercicio, /meta ou /relatorio.");
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sender.enviarMensagem(chatId, "⚠️ Uso incorreto! Use: /aprovar <ID_DO_USUARIO>");
            return;
        }
        try {
            Long targetId = Long.parseLong(parts[1]);
            Optional<UserTelegram> optUser = userService.approveUser(targetId);
            if (optUser.isPresent()) {
                UserTelegram u = optUser.get();
                String name = u.getFirstName() != null ? messageFormatter.escapeMarkdown(u.getFirstName()) : "Usuário";
                sender.enviarMensagemMarkdown(chatId, String.format("✅ *Usuário Aprovado\\!*\n\n👤 *Nome:* %s\n🆔 *ID:* `%d`", name, u.getId()));
                sender.enviarMensagemMarkdown(targetId, "🎉 *Acesso Aprovado\\!*\n\nSeu acesso ao *ShapeLog\\.ai* foi liberado pelo administrador\\! Envie /start para ver os comandos disponíveis\\.");
            } else {
                sender.enviarMensagem(chatId, "⚠️ Usuário não encontrado no banco de dados com o ID fornecido.");
            }
        } catch (NumberFormatException e) {
            sender.enviarMensagem(chatId, "⚠️ ID inválido! O ID deve ser um número.");
        }
    }

    private void rotearRevogar(long chatId, String text, BotActionSender sender) {
        if (!isAdmin(chatId)) {
            sender.enviarMensagem(chatId, "Comando não reconhecido. Use /refeicao, /treino, /exercicio, /meta ou /relatorio.");
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            sender.enviarMensagem(chatId, "⚠️ Uso incorreto! Use: /revogar <ID_DO_USUARIO>");
            return;
        }
        try {
            Long targetId = Long.parseLong(parts[1]);
            Optional<UserTelegram> optUser = userService.revokeUser(targetId);
            if (optUser.isPresent()) {
                UserTelegram u = optUser.get();
                String name = u.getFirstName() != null ? messageFormatter.escapeMarkdown(u.getFirstName()) : "Usuário";
                sender.enviarMensagemMarkdown(chatId, String.format("❌ *Acesso Revogado\\!*\n\n👤 *Nome:* %s\n🆔 *ID:* `%d`", name, u.getId()));
            } else {
                sender.enviarMensagem(chatId, "⚠️ Usuário não encontrado no banco de dados.");
            }
        } catch (NumberFormatException e) {
            sender.enviarMensagem(chatId, "⚠️ ID inválido! O ID deve ser um número.");
        }
    }

    private void rotearListarUsuarios(long chatId, BotActionSender sender) {
        if (!isAdmin(chatId)) {
            sender.enviarMensagem(chatId, "Comando não reconhecido. Use /refeicao, /treino, /exercicio, /meta ou /relatorio.");
            return;
        }
        List<UserTelegram> approvedUsers = userService.listApprovedUsers();
        if (approvedUsers.isEmpty()) {
            sender.enviarMensagem(chatId, "Nenhum usuário aprovado no momento.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Usuários Aprovados:*\n\n");
        for (UserTelegram u : approvedUsers) {
            String name = u.getFirstName() != null ? messageFormatter.escapeMarkdown(u.getFirstName()) : "Sem nome";
            String uname = u.getUsername() != null ? "@" + messageFormatter.escapeMarkdown(u.getUsername()) : "";
            sb.append(String.format("• %s %s \\(ID: `%d`\\)\n", name, uname, u.getId()));
        }
        sender.enviarMensagemMarkdown(chatId, sb.toString().trim());
    }

    private boolean isAdmin(long chatId) {
        return adminId != null && adminId > 0 && adminId.equals(chatId);
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
        stateManager.removeState(chatId);
        switch (state) {
            case AWAITING_MEAL ->
                mealHandler.registrarRefeicao(user, text, null, userMessageId, chatId, sender);
            case AWAITING_WORKOUT_TITLE ->
                iniciarOuConfirmarTituloTreino(user, text, userMessageId, chatId, sender);
            case AWAITING_EXERCISE ->
                workoutHandler.registrarExercicioOuCriarTreino(user, text, null, userMessageId, chatId, sender);
            case AWAITING_EDIT_MEAL -> {
                Long mealId = UserState.extractId(rawState);
                mealHandler.atualizarRefeicaoEditada(user, mealId, text, null, chatId, sender);
            }
            case AWAITING_EDIT_EXERCISE ->
                processarEdicaoExercicio(rawState, text, null, chatId, sender);
            case AWAITING_GOAL ->
                processarEntradaMeta(user, text, chatId, sender);
            default ->
                sender.enviarMensagem(chatId, "Estado não reconhecido. Envie um comando para continuar.");
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
        stateManager.removeState(chatId);
        switch (state) {
            case AWAITING_MEAL ->
                mealHandler.registrarRefeicao(user, null, audioBytes, null, chatId, sender);
            case AWAITING_WORKOUT_TITLE -> {
                stateManager.putState(chatId, UserState.AWAITING_WORKOUT_TITLE.serialize());
                sender.enviarMensagem(chatId, "⚠️ Por favor, digite o título do treino em texto primeiro!");
            }
            case AWAITING_EXERCISE ->
                workoutHandler.registrarExercicioOuCriarTreino(user, null, audioBytes, null, chatId, sender);
            case AWAITING_EDIT_MEAL -> {
                Long mealId = UserState.extractId(rawState);
                mealHandler.atualizarRefeicaoEditada(user, mealId, null, audioBytes, chatId, sender);
            }
            case AWAITING_EDIT_EXERCISE ->
                processarEdicaoExercicio(rawState, null, audioBytes, chatId, sender);
            case AWAITING_GOAL ->
                goalHandler.sugerirMetasPorIA(user, null, audioBytes, chatId, sender);
            default ->
                sender.enviarMensagem(chatId, "Estado não reconhecido. Envie um comando para continuar.");
        }
    }

    private void processarEdicaoExercicio(String rawState, String text, byte[] audioBytes, long chatId, BotActionSender sender) {
        String payload = UserState.extractPayload(rawState);
        if (payload != null && payload.contains(":")) {
            String[] parts = payload.split(":");
            Long workoutId = Long.parseLong(parts[0]);
            String exerciseId = parts[1];
            Integer originalBotMsgId = parts.length >= 3 ? Integer.parseInt(parts[2]) : null;
            workoutHandler.atualizarExercicioEditado(workoutId, exerciseId, originalBotMsgId, text, audioBytes, chatId, sender);
        }
    }

    public void rotearCallback(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        try {
            if (data.startsWith("pending_") || data.startsWith("wk_") || "cancel_state".equals(data)) {
                rotearCallbackPendenteOuEstado(user, data, botMessageId, chatId, sender);
            } else if (data.startsWith("confirm_goal:") || data.startsWith("open_report:") || data.startsWith("rep_")) {
                rotearCallbackMetaOuRelatorio(user, data, botMessageId, chatId, sender);
            } else if (data.startsWith("delete_meal:")) {
                Long mealId = Long.parseLong(data.split(":")[1]);
                Meal meal = mealService.findById(mealId).orElse(null);
                if (meal == null || !meal.getUser().getId().equals(chatId)) {
                    log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} mealId={}", chatId, mealId);
                    return;
                }
                mealHandler.processarExclusaoRefeicao(mealId, botMessageId, chatId, sender);
            } else if (data.startsWith("delete_workout:")) {
                Long workoutId = Long.parseLong(data.split(":")[1]);
                WorkoutSession ws = workoutService.findById(workoutId).orElse(null);
                if (ws == null || !ws.getUser().getId().equals(chatId)) {
                    log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} workoutId={}", chatId, workoutId);
                    return;
                }
                workoutHandler.processarExclusaoTreino(workoutId, botMessageId, chatId, sender);
            } else if (data.startsWith("edit_meal:")) {
                Long mealId = Long.parseLong(data.split(":")[1]);
                Meal meal = mealService.findById(mealId).orElse(null);
                if (meal == null || !meal.getUser().getId().equals(chatId)) {
                    log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} mealId={}", chatId, mealId);
                    return;
                }
                stateManager.putState(chatId, UserState.AWAITING_EDIT_MEAL.withId(mealId));
                sender.enviarMensagemMarkdown(
                        chatId,
                        "✏️ *Editando refeição:* diga o que deseja corrigir \\(ex: _'foram 150g de arroz'_ ou _'troca o queijo por 2 ovos'_\\), ou descreva a refeição novamente\\.",
                        keyboardFactory.criarBotaoCancelarEstado()
                );
            } else if (data.startsWith("edit_ex:")) {
                String[] parts = data.split(":");
                if (parts.length < 3) { log.error("Callback edit_ex malformado: '{}'", data); return; }
                Long workoutId = Long.parseLong(parts[1]);
                WorkoutSession ws = workoutService.findById(workoutId).orElse(null);
                if (ws == null || !ws.getUser().getId().equals(chatId)) {
                    log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} workoutId={}", chatId, workoutId);
                    return;
                }
                String exerciseId = parts[2];
                stateManager.putState(chatId, UserState.AWAITING_EDIT_EXERCISE.withPayload(workoutId + ":" + exerciseId + ":" + botMessageId));
                sender.enviarMensagemMarkdown(
                        chatId,
                        "✏️ *Editando exercício:* envie um texto ou grave um áudio com o novo conteúdo para este exercício\\.",
                        keyboardFactory.criarBotaoCancelarEstado()
                );
            } else if (data.startsWith("delete_ex:")) {
                String[] parts = data.split(":");
                if (parts.length < 3) { log.error("Callback delete_ex malformado: '{}'", data); return; }
                Long workoutId = Long.parseLong(parts[1]);
                WorkoutSession ws = workoutService.findById(workoutId).orElse(null);
                if (ws == null || !ws.getUser().getId().equals(chatId)) {
                    log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} workoutId={}", chatId, workoutId);
                    return;
                }
                String exerciseId = parts[2];
                workoutHandler.processarExclusaoExercicio(workoutId, exerciseId, botMessageId, chatId, sender);
            } else if (data.startsWith("del_rep_m:")) {
                Long mealId = Long.parseLong(data.split(":")[1]);
                Meal meal = mealService.findById(mealId).orElse(null);
                if (meal == null || !meal.getUser().getId().equals(chatId)) {
                    log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} mealId={}", chatId, mealId);
                    return;
                }
                LocalDate date = meal.getCreatedAt() != null ? meal.getCreatedAt().toLocalDate() : LocalDate.now();
                mealService.deleteMeal(mealId);
                reportHandler.atualizarRelatorioAposExclusao(user, date, botMessageId, chatId, sender);
            } else if (data.startsWith("del_rep_w:")) {
                Long workoutId = Long.parseLong(data.split(":")[1]);
                WorkoutSession ws = workoutService.findById(workoutId).orElse(null);
                if (ws == null || !ws.getUser().getId().equals(chatId)) {
                    log.warn("SEC-04: tentativa de acesso não autorizado. chatId={} workoutId={}", chatId, workoutId);
                    return;
                }
                LocalDate date = ws.getCreatedAt() != null ? ws.getCreatedAt().toLocalDate() : LocalDate.now();
                workoutService.deleteWorkout(workoutId);
                reportHandler.atualizarRelatorioAposExclusao(user, date, botMessageId, chatId, sender);
            }
        } catch (Exception e) {
            log.error("Erro ao processar callback data='{}' para chatId={}", data, chatId, e);
            sender.enviarMensagem(chatId, "Ocorreu um erro ao processar sua solicitação.");
        }
    }

    private void rotearCallbackPendenteOuEstado(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        if ("cancel_state".equals(data)) {
            stateManager.removeState(chatId);
            sender.editarMensagemBot(chatId, botMessageId, "✖️ Operação cancelada\\.", null);
            return;
        }
        if (data.startsWith("pending_meal:")) {
            String token = data.substring("pending_meal:".length());
            PendingInput pending = stateManager.consumePendingInput(chatId, token);
            if (pending == null) {
                sender.editarMensagemBot(chatId, botMessageId, "⚠️ Esta entrada expirou ou já foi processada\\. Envie novamente\\.", null);
                return;
            }
            sender.deletarMensagem(chatId, botMessageId);
            mealHandler.registrarRefeicao(user, pending.text(), pending.audioBytes(), pending.userMessageId(), chatId, sender);
        } else if (data.startsWith("pending_exercise:")) {
            String token = data.substring("pending_exercise:".length());
            PendingInput pending = stateManager.consumePendingInput(chatId, token);
            if (pending == null) {
                sender.editarMensagemBot(chatId, botMessageId, "⚠️ Esta entrada expirou ou já foi processada\\. Envie novamente\\.", null);
                return;
            }
            sender.deletarMensagem(chatId, botMessageId);
            workoutHandler.registrarExercicioOuCriarTreino(user, pending.text(), pending.audioBytes(), pending.userMessageId(), chatId, sender);
        } else if (data.startsWith("pending_cancel:")) {
            String token = data.substring("pending_cancel:".length());
            stateManager.consumePendingInput(chatId, token);
            sender.editarMensagemBot(chatId, botMessageId, "✖️ Ação cancelada\\.", null);
        } else if (data.startsWith("wk_rename:")) {
            String token = data.substring("wk_rename:".length());
            PendingInput pending = stateManager.consumePendingInput(chatId, token);
            if (pending == null) {
                sender.editarMensagemBot(chatId, botMessageId, "⚠️ Esta solicitação expirou\\. Envie /treino novamente\\.", null);
                return;
            }
            sender.deletarMensagem(chatId, botMessageId);
            workoutHandler.criarRascunho(user, pending.text(), pending.userMessageId(), chatId, sender);
            stateManager.putState(chatId, UserState.AWAITING_EXERCISE.serialize());
        } else if (data.startsWith("wk_new:")) {
            String token = data.substring("wk_new:".length());
            PendingInput pending = stateManager.consumePendingInput(chatId, token);
            if (pending == null) {
                sender.editarMensagemBot(chatId, botMessageId, "⚠️ Esta solicitação expirou\\. Envie /treino novamente\\.", null);
                return;
            }
            sender.deletarMensagem(chatId, botMessageId);
            workoutHandler.criarNovoTreino(user, pending.text(), pending.userMessageId(), chatId, sender);
            stateManager.putState(chatId, UserState.AWAITING_EXERCISE.serialize());
        }
    }

    private void rotearCallbackMetaOuRelatorio(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        if (data.startsWith("confirm_goal:")) {
            String[] parts = data.split(":");
            if (parts.length < 5) {
                log.error("Callback confirm_goal malformado: '{}'", data);
                return;
            }
            int cal = Integer.parseInt(parts[1]);
            int prot = Integer.parseInt(parts[2]);
            int carb = Integer.parseInt(parts[3]);
            int fat = Integer.parseInt(parts[4]);
            stateManager.removeState(chatId);
            goalHandler.confirmarMetasSugeridas(user, cal, prot, carb, fat, botMessageId, chatId, sender);
        } else if (data.startsWith("open_report:")) {
            LocalDate date = LocalDate.parse(data.substring("open_report:".length()));
            reportHandler.abrirRelatorioPorData(user, date, chatId, sender);
        } else if (data.startsWith("rep_nav:")) {
            LocalDate date = LocalDate.parse(data.substring("rep_nav:".length()));
            reportHandler.navegarRelatorio(user, date, botMessageId, chatId, sender);
        } else if (data.startsWith("rep_manage:")) {
            LocalDate date = LocalDate.parse(data.substring("rep_manage:".length()));
            reportHandler.abrirModoGerenciar(user, date, botMessageId, chatId, sender);
        } else if (data.startsWith("rep_view:")) {
            LocalDate date = LocalDate.parse(data.substring("rep_view:".length()));
            reportHandler.voltarModoVisualizacao(user, date, botMessageId, chatId, sender);
        }
    }

    private void rotearMeta(UserTelegram user, String argumento, long chatId, BotActionSender sender) {
        if (argumento == null || argumento.isBlank()) {
            stateManager.putState(chatId, UserState.AWAITING_GOAL.serialize());
            goalHandler.mostrarMetasAtuais(user, chatId, sender);
            return;
        }
        processarEntradaMeta(user, argumento, chatId, sender);
    }

    private void processarEntradaMeta(UserTelegram user, String input, long chatId, BotActionSender sender) {
        String trimmed = input != null ? input.trim() : "";
        if (trimmed.isEmpty()) {
            sender.enviarMensagem(chatId, "⚠️ Informe os 4 valores ou descreva seu objetivo.");
            return;
        }
        String[] partes = trimmed.split("\\s+");
        boolean allNumbers = Arrays.stream(partes).allMatch(p -> p.matches("-?\\d+"));
        if (allNumbers) {
            if (partes.length != 4) {
                sender.enviarMensagem(chatId, "⚠️ Formato incorreto! Use assim:\n/meta <calorias> <proteínas> <carbos> <gorduras>\n\n💡 Exemplo: /meta 2000 150 200 60\nOu descreva seu objetivo em texto (ex: /meta peso 80kg quero secar).");
                return;
            }
            try {
                int cal  = Integer.parseInt(partes[0]);
                int prot = Integer.parseInt(partes[1]);
                int carb = Integer.parseInt(partes[2]);
                int fat  = Integer.parseInt(partes[3]);
                if (cal <= 0 || prot <= 0 || carb <= 0 || fat <= 0) {
                    sender.enviarMensagem(chatId, "⚠️ Os valores das metas devem ser números maiores que zero!\n\n💡 Exemplo: /meta 2000 150 200 60");
                    return;
                }
                goalHandler.salvarMetasDiretas(user, cal, prot, carb, fat, chatId, sender);
            } catch (NumberFormatException e) {
                sender.enviarMensagem(chatId, "Erro: Os valores das metas devem ser números inteiros!");
            }
            return;
        }
        goalHandler.sugerirMetasPorIA(user, trimmed, null, chatId, sender);
    }
}
