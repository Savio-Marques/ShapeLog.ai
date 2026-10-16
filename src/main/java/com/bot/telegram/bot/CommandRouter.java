package com.bot.telegram.bot;

import com.bot.telegram.bot.handler.MealHandler;
import com.bot.telegram.bot.handler.ReportHandler;
import com.bot.telegram.bot.handler.WorkoutHandler;
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
import java.util.List;
import java.util.Optional;

@Component
public class CommandRouter {

    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);
    private final MealHandler mealHandler;
    private final WorkoutHandler workoutHandler;
    private final ReportHandler reportHandler;
    private final MessageFormatter messageFormatter;
    private final MealService mealService;
    private final IWorkoutService workoutService;
    private final StateManager stateManager;
    private final UserService userService;

    @Value("${telegram.bot.admin-id:0}")
    private Long adminId;

    public CommandRouter(MealHandler mealHandler, WorkoutHandler workoutHandler,
                         ReportHandler reportHandler, MessageFormatter messageFormatter,
                         MealService mealService, IWorkoutService workoutService,
                         StateManager stateManager, UserService userService) {
        this.mealHandler = mealHandler;
        this.workoutHandler = workoutHandler;
        this.reportHandler = reportHandler;
        this.messageFormatter = messageFormatter;
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
                rotearMeta(user, trimmed, chatId, sender);
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
                } else {
                    workoutHandler.criarRascunho(user, argumento, userMessageId, chatId, sender);
                }
            }
            case "/exercicio" -> {
                Optional<WorkoutSession> optWorkout = workoutService.getLatestWorkoutForToday(user);
                if (optWorkout.isEmpty()) {
                    sender.enviarMensagem(chatId, "⚠️ Você precisa iniciar um treino hoje primeiro! Use /treino <Titulo>");
                } else if (argumento.isEmpty()) {
                    stateManager.putState(chatId, UserState.AWAITING_EXERCISE.serialize());
                    sender.enviarMensagem(chatId, "🎙️ Envie um áudio ou descreva em texto o exercício agora!");
                } else {
                    workoutHandler.registrarExercicio(optWorkout.get().getId(), argumento, null, chatId, sender);
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
            sender.enviarMensagem(chatId, "💡 Para registrar algo, use primeiro um dos comandos:\n🥗 /refeicao\n🏋️‍♂️ /treino\n💪 /exercicio\n\nDepois é só descrever em texto ou enviar um áudio!");
            return;
        }
        stateManager.removeState(chatId);
        switch (state) {
            case AWAITING_MEAL ->
                mealHandler.registrarRefeicao(user, text, null, userMessageId, chatId, sender);
            case AWAITING_WORKOUT_TITLE ->
                workoutHandler.criarRascunho(user, text, userMessageId, chatId, sender);
            case AWAITING_EXERCISE -> {
                Optional<WorkoutSession> opt = workoutService.getLatestWorkoutForToday(user);
                if (opt.isPresent()) {
                    workoutHandler.registrarExercicio(opt.get().getId(), text, null, chatId, sender);
                } else {
                    sender.enviarMensagem(chatId, "⚠️ Nenhum treino ativo encontrado para hoje. Use /treino primeiro.");
                }
            }
            case AWAITING_EDIT_MEAL -> {
                Long mealId = UserState.extractId(rawState);
                mealHandler.atualizarRefeicaoEditada(mealId, text, null, chatId, sender);
            }
            case AWAITING_EDIT_WORKOUT ->
                sender.enviarMensagem(chatId, "⚠️ Edição do treino inteiro não está disponível. Use os botões '✏️ Editar' ao lado de cada exercício.");
            case AWAITING_EDIT_EXERCISE -> {
                String payload = UserState.extractPayload(rawState);
                if (payload != null && payload.contains(":")) {
                    Long workoutId = Long.parseLong(payload.split(":")[0]);
                    String exerciseId = payload.split(":")[1];
                    workoutHandler.atualizarExercicioEditado(workoutId, exerciseId, text, null, chatId, sender);
                }
            }
            default ->
                sender.enviarMensagem(chatId, "Estado não reconhecido. Envie um comando para continuar.");
        }
    }

    public void rotearEstadoVoz(UserTelegram user, byte[] audioBytes, long chatId, BotActionSender sender) {
        String rawState = stateManager.getState(chatId);
        UserState state = UserState.from(rawState);
        if (state == null) {
            sender.enviarMensagem(chatId, "💡 Para enviar um áudio, envie primeiro o comando correspondente (/refeicao, /treino ou /exercicio) e em seguida grave o áudio.");
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
            case AWAITING_EXERCISE -> {
                Optional<WorkoutSession> opt = workoutService.getLatestWorkoutForToday(user);
                if (opt.isPresent()) {
                    workoutHandler.registrarExercicio(opt.get().getId(), null, audioBytes, chatId, sender);
                } else {
                    sender.enviarMensagem(chatId, "⚠️ Nenhum treino ativo encontrado para hoje. Use /treino primeiro.");
                }
            }
            case AWAITING_EDIT_MEAL -> {
                Long mealId = UserState.extractId(rawState);
                mealHandler.atualizarRefeicaoEditada(mealId, null, audioBytes, chatId, sender);
            }
            case AWAITING_EDIT_WORKOUT ->
                sender.enviarMensagem(chatId, "⚠️ Edição do treino inteiro não está disponível. Use os botões '✏️ Editar' ao lado de cada exercício.");
            case AWAITING_EDIT_EXERCISE -> {
                String payload = UserState.extractPayload(rawState);
                if (payload != null && payload.contains(":")) {
                    Long workoutId = Long.parseLong(payload.split(":")[0]);
                    String exerciseId = payload.split(":")[1];
                    workoutHandler.atualizarExercicioEditado(workoutId, exerciseId, null, audioBytes, chatId, sender);
                }
            }
            default ->
                sender.enviarMensagem(chatId, "Estado não reconhecido. Envie um comando para continuar.");
        }
    }

    public void rotearCallback(UserTelegram user, String data, int botMessageId, long chatId, BotActionSender sender) {
        try {
            if (data.startsWith("delete_meal:")) {
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
                sender.enviarMensagem(chatId, "✏️ Envie um texto ou grave um áudio com o novo conteúdo para esta refeição.");
            } else if (data.startsWith("edit_workout:")) {
                sender.enviarMensagem(chatId, "⚠️ Edição do treino inteiro não está disponível. Use os botões '✏️ Editar' ao lado de cada exercício individualmente.");
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
                stateManager.putState(chatId, UserState.AWAITING_EDIT_EXERCISE.withPayload(workoutId + ":" + exerciseId));
                sender.enviarMensagem(chatId, "✏️ Envie um texto ou grave um áudio com o novo conteúdo para este exercício.");
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

    private void rotearMeta(UserTelegram user, String text, long chatId, BotActionSender sender) {
        String[] partes = text.split("\\s+");
        if (partes.length < 5) {
            sender.enviarMensagem(chatId, "⚠️ Formato incorreto! Use assim:\n/meta <calorias> <proteínas> <carbos> <gorduras>\n\n💡 Exemplo: /meta 2000 150 200 60");
            return;
        }
        try {
            int cal  = Integer.parseInt(partes[1]);
            int prot = Integer.parseInt(partes[2]);
            int carb = Integer.parseInt(partes[3]);
            int fat  = Integer.parseInt(partes[4]);
            if (cal <= 0 || prot <= 0 || carb <= 0 || fat <= 0) {
                sender.enviarMensagem(chatId, "⚠️ Os valores das metas devem ser números maiores que zero!\n\n💡 Exemplo: /meta 2000 150 200 60");
                return;
            }
            userService.updateGoals(user, cal, prot, carb, fat);
            sender.enviarMensagemMarkdown(chatId, messageFormatter.formatGoalsUpdated(cal, prot, carb, fat));
        } catch (NumberFormatException e) {
            sender.enviarMensagem(chatId, "Erro: Os valores das metas devem ser números inteiros!");
        }
    }
}
