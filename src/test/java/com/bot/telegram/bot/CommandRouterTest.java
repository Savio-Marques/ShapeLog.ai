package com.bot.telegram.bot;

import com.bot.telegram.bot.handler.GoalHandler;
import com.bot.telegram.bot.handler.MealHandler;
import com.bot.telegram.bot.handler.ReportHandler;
import com.bot.telegram.bot.handler.WorkoutHandler;
import com.bot.telegram.bot.keyboard.InlineKeyboardFactory;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.IWorkoutService;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandRouterTest {

    private MealHandler mealHandler;
    private WorkoutHandler workoutHandler;
    private ReportHandler reportHandler;
    private GoalHandler goalHandler;
    private MessageFormatter messageFormatter;
    private InlineKeyboardFactory keyboardFactory;
    private MealService mealService;
    private IWorkoutService workoutService;
    private StateManager stateManager;
    private UserService userService;
    private BotActionSender sender;
    private CommandRouter commandRouter;

    private UserTelegram user;
    private final Long ADMIN_ID = 999999L;
    private final Long USER_ID = 123456L;

    @BeforeEach
    void setUp() {
        mealHandler = mock(MealHandler.class);
        workoutHandler = mock(WorkoutHandler.class);
        reportHandler = mock(ReportHandler.class);
        goalHandler = mock(GoalHandler.class);
        messageFormatter = mock(MessageFormatter.class);
        keyboardFactory = new InlineKeyboardFactory();
        mealService = mock(MealService.class);
        workoutService = mock(IWorkoutService.class);
        stateManager = mock(StateManager.class);
        userService = mock(UserService.class);
        sender = mock(BotActionSender.class);

        commandRouter = new CommandRouter(
                mealHandler, workoutHandler, reportHandler, goalHandler,
                messageFormatter, keyboardFactory,
                mealService, workoutService,
                stateManager, userService
        );

        ReflectionTestUtils.setField(commandRouter, "adminId", ADMIN_ID);

        user = UserTelegram.builder()
                .id(USER_ID)
                .firstName("Sávio")
                .approved(true)
                .build();
    }

    @Test
    void rotearComando_start_deveEnviarMensagemDeBoasVindas() {
        when(messageFormatter.formatStart("Sávio")).thenReturn("Boas-vindas!");
        commandRouter.rotearComando(user, "/start", 1, USER_ID, sender);
        verify(sender).enviarMensagemMarkdown(USER_ID, "Boas-vindas!");
    }

    @Test
    void rotearComando_refeicaoSemTexto_deveColocarEstadoAwaitingMeal() {
        commandRouter.rotearComando(user, "/refeicao", 1, USER_ID, sender);
        verify(stateManager).putState(eq(USER_ID), contains("AWAITING_MEAL"));
        verify(sender).enviarMensagem(eq(USER_ID), contains("áudio ou descreva"));
    }

    @Test
    void rotearComando_refeicaoComTexto_deveChamarMealHandler() {
        commandRouter.rotearComando(user, "/refeicao Arroz e feijão", 1, USER_ID, sender);
        verify(mealHandler).registrarRefeicao(eq(user), eq("Arroz e feijão"), isNull(), eq(1), eq(USER_ID), eq(sender));
    }

    @Test
    void rotearComando_treinoSemTexto_deveColocarEstadoAwaitingTitle() {
        commandRouter.rotearComando(user, "/treino", 1, USER_ID, sender);
        verify(stateManager).putState(eq(USER_ID), contains("AWAITING_WORKOUT_TITLE"));
        verify(sender).enviarMensagem(eq(USER_ID), contains("título do seu treino"));
    }

    @Test
    void rotearComando_treinoComTexto_deveCriarRascunhoETransicionarParaExercicio() {
        when(workoutService.getLatestWorkoutForToday(user)).thenReturn(Optional.empty());
        commandRouter.rotearComando(user, "/treino Peito", 1, USER_ID, sender);
        verify(workoutHandler).criarRascunho(eq(user), eq("Peito"), eq(1), eq(USER_ID), eq(sender));
        verify(stateManager).putState(USER_ID, UserState.AWAITING_EXERCISE.serialize());
    }

    @Test
    void rotearComando_treinoComSeriesECarga_deveIrDiretoParaExercicio() {
        commandRouter.rotearComando(user, "/treino Supino reto 4x10 80kg", 1, USER_ID, sender);
        verify(workoutHandler).registrarExercicioOuCriarTreino(eq(user), eq("Supino reto 4x10 80kg"), isNull(), eq(1), eq(USER_ID), eq(sender));
        verify(workoutHandler, never()).criarRascunho(any(), any(), any(), anyLong(), any());
    }

    @Test
    void rotearComando_treinoComCardioOuCorrida_deveIrDiretoParaExercicio() {
        commandRouter.rotearComando(user, "/treino Esteira 30 min", 1, USER_ID, sender);
        commandRouter.rotearComando(user, "/treino Corrida 10km", 2, USER_ID, sender);
        verify(workoutHandler).registrarExercicioOuCriarTreino(eq(user), eq("Esteira 30 min"), isNull(), eq(1), eq(USER_ID), eq(sender));
        verify(workoutHandler).registrarExercicioOuCriarTreino(eq(user), eq("Corrida 10km"), isNull(), eq(2), eq(USER_ID), eq(sender));
        verify(workoutHandler, never()).criarRascunho(any(), any(), any(), anyLong(), any());
    }

    @Test
    void rotearComando_treinoHyroxOuCrossfitSemMetricas_deveCriarTitulo() {
        when(workoutService.getLatestWorkoutForToday(user)).thenReturn(Optional.empty());
        commandRouter.rotearComando(user, "/treino Hyrox", 1, USER_ID, sender);
        commandRouter.rotearComando(user, "/treino CrossFit", 2, USER_ID, sender);
        verify(workoutHandler).criarRascunho(eq(user), eq("Hyrox"), eq(1), eq(USER_ID), eq(sender));
        verify(workoutHandler).criarRascunho(eq(user), eq("CrossFit"), eq(2), eq(USER_ID), eq(sender));
        verify(stateManager, times(2)).putState(USER_ID, UserState.AWAITING_EXERCISE.serialize());
    }

    @Test
    void rotearComando_treinoComSessaoPreenchidaHoje_devePerguntarRenomearOuNovo() {
        WorkoutSession existing = WorkoutSession.builder()
                .id(50L)
                .user(user)
                .rawInput("Peito")
                .exercisesJson("[{\"id\":\"ex1\",\"name\":\"Supino\",\"series\":[]}]")
                .build();
        when(workoutService.getLatestWorkoutForToday(user)).thenReturn(Optional.of(existing));
        when(workoutService.deserializeExercises(existing.getExercisesJson())).thenReturn(
                List.of(com.bot.telegram.dto.WorkoutDto.ExerciseDto.builder().id("ex1").name("Supino").build())
        );
        when(stateManager.putPendingInput(USER_ID, "Cardio", null, 1)).thenReturn("tok12345");
        when(messageFormatter.formatWorkoutConflictPrompt("Peito", "Cardio")).thenReturn("Conflito");

        commandRouter.rotearComando(user, "/treino Cardio", 1, USER_ID, sender);
        verify(sender).enviarMensagemMarkdown(eq(USER_ID), eq("Conflito"), any(InlineKeyboardMarkup.class));
        verify(workoutHandler, never()).criarRascunho(any(), any(), any(), anyLong(), any());
    }

    @Test
    void rotearComando_exercicioSemTreinoHoje_deveAutoCriarViaHandler() {
        commandRouter.rotearComando(user, "/exercicio Supino 3x10 70kg", 2, USER_ID, sender);
        verify(workoutHandler).registrarExercicioOuCriarTreino(eq(user), eq("Supino 3x10 70kg"), isNull(), eq(2), eq(USER_ID), eq(sender));
    }

    @Test
    void rotearComando_metaVazio_deveMostrarMetasAtuaisEColocarEstado() {
        commandRouter.rotearComando(user, "/meta", 1, USER_ID, sender);
        verify(stateManager).putState(USER_ID, UserState.AWAITING_GOAL.serialize());
        verify(goalHandler).mostrarMetasAtuais(user, USER_ID, sender);
    }

    @Test
    void rotearComando_metaComValoresCorretos_deveChamarGoalHandler() {
        commandRouter.rotearComando(user, "/meta 2000 150 200 60", 1, USER_ID, sender);
        verify(goalHandler).salvarMetasDiretas(user, 2000, 150, 200, 60, USER_ID, sender);
    }

    @Test
    void rotearComando_metaTextoNatural_deveSugerirViaIA() {
        commandRouter.rotearComando(user, "/meta peso 80kg quero secar", 1, USER_ID, sender);
        verify(goalHandler).sugerirMetasPorIA(user, "peso 80kg quero secar", null, USER_ID, sender);
    }

    @Test
    void rotearComando_metaSemValoresSuficientes_deveMostrarErro() {
        commandRouter.rotearComando(user, "/meta 2000", 1, USER_ID, sender);
        verify(sender).enviarMensagem(eq(USER_ID), contains("Formato incorreto"));
        verifyNoInteractions(goalHandler);
    }

    @Test
    void rotearComando_comandoDesconhecido_deveMostrarListaDeComandos() {
        commandRouter.rotearComando(user, "/comandoinvalido", 1, USER_ID, sender);
        verify(sender).enviarMensagem(eq(USER_ID), contains("Comando não reconhecido"));
    }

    @Test
    void rotearComando_metaComValoresNegativosOuZero_deveMostrarErro() {
        commandRouter.rotearComando(user, "/meta 0 150 200 60", 1, USER_ID, sender);
        verify(sender).enviarMensagem(eq(USER_ID), contains("maiores que zero"));
        verifyNoInteractions(goalHandler);

        commandRouter.rotearComando(user, "/meta 2000 -10 200 60", 1, USER_ID, sender);
        verify(sender, times(2)).enviarMensagem(eq(USER_ID), contains("maiores que zero"));
    }

    @Test
    void rotearEstadoTexto_semEstado_deveGuardarPendenteEPedirConfirmacao() {
        when(stateManager.getState(USER_ID)).thenReturn(null);
        when(stateManager.putPendingInput(USER_ID, "2 ovos e 1 pão", null, 10)).thenReturn("abcd1234");
        when(messageFormatter.formatPendingInputPrompt("2 ovos e 1 pão", false)).thenReturn("Prompt");

        commandRouter.rotearEstadoTexto(user, "2 ovos e 1 pão", 10, USER_ID, sender);

        verify(stateManager).putPendingInput(USER_ID, "2 ovos e 1 pão", null, 10);
        verify(sender).enviarMensagemMarkdown(eq(USER_ID), eq("Prompt"), any(InlineKeyboardMarkup.class));
        verifyNoInteractions(mealHandler, workoutHandler);
    }

    @Test
    void rotearEstadoVoz_semEstado_deveGuardarAudioPendenteEPedirConfirmacao() {
        byte[] audio = new byte[]{1, 2, 3};
        when(stateManager.getState(USER_ID)).thenReturn(null);
        when(stateManager.putPendingInput(USER_ID, null, audio, null)).thenReturn("voz12345");
        when(messageFormatter.formatPendingInputPrompt(null, true)).thenReturn("Prompt Voz");

        commandRouter.rotearEstadoVoz(user, audio, USER_ID, sender);

        verify(stateManager).putPendingInput(USER_ID, null, audio, null);
        verify(sender).enviarMensagemMarkdown(eq(USER_ID), eq("Prompt Voz"), any(InlineKeyboardMarkup.class));
        verifyNoInteractions(mealHandler, workoutHandler);
    }

    @Test
    void rotearCallback_pendingMeal_validoEExpirado() {
        byte[] audio = new byte[]{9, 8};
        PendingInput pending = new PendingInput("tok1", null, audio, 77, System.currentTimeMillis());
        when(stateManager.consumePendingInput(USER_ID, "tok1")).thenReturn(pending);

        commandRouter.rotearCallback(user, "pending_meal:tok1", 500, USER_ID, sender);
        verify(sender).deletarMensagem(USER_ID, 500);
        verify(mealHandler).registrarRefeicao(user, null, audio, 77, USER_ID, sender);

        when(stateManager.consumePendingInput(USER_ID, "old")).thenReturn(null);
        commandRouter.rotearCallback(user, "pending_meal:old", 501, USER_ID, sender);
        verify(sender).editarMensagemBot(eq(USER_ID), eq(501), contains("expirou"), isNull());
    }

    @Test
    void rotearCallback_pendingExerciseEPendingCancel() {
        PendingInput pending = new PendingInput("tok2", "Supino 3x10", null, 88, System.currentTimeMillis());
        when(stateManager.consumePendingInput(USER_ID, "tok2")).thenReturn(pending);

        commandRouter.rotearCallback(user, "pending_exercise:tok2", 600, USER_ID, sender);
        verify(sender).deletarMensagem(USER_ID, 600);
        verify(workoutHandler).registrarExercicioOuCriarTreino(user, "Supino 3x10", null, 88, USER_ID, sender);

        commandRouter.rotearCallback(user, "pending_cancel:tok3", 601, USER_ID, sender);
        verify(stateManager).consumePendingInput(USER_ID, "tok3");
        verify(sender).editarMensagemBot(eq(USER_ID), eq(601), contains("cancelada"), isNull());
    }

    @Test
    void rotearCallback_wkNewEWkRename_devemExecutarEEntrarEmAwaitingExercise() {
        PendingInput p1 = new PendingInput("wk1", "Cardio", null, 11, System.currentTimeMillis());
        when(stateManager.consumePendingInput(USER_ID, "wk1")).thenReturn(p1);
        commandRouter.rotearCallback(user, "wk_new:wk1", 700, USER_ID, sender);
        verify(workoutHandler).criarNovoTreino(user, "Cardio", 11, USER_ID, sender);
        verify(stateManager).putState(USER_ID, UserState.AWAITING_EXERCISE.serialize());

        PendingInput p2 = new PendingInput("wk2", "Peito e Ombro", null, 12, System.currentTimeMillis());
        when(stateManager.consumePendingInput(USER_ID, "wk2")).thenReturn(p2);
        commandRouter.rotearCallback(user, "wk_rename:wk2", 701, USER_ID, sender);
        verify(workoutHandler).criarRascunho(user, "Peito e Ombro", 12, USER_ID, sender);
        verify(stateManager, times(2)).putState(USER_ID, UserState.AWAITING_EXERCISE.serialize());
    }

    @Test
    void rotearCallback_editExEEdicaoEmSeguida_devePreservarBotMessageIdParaEdicaoInPlace() {
        WorkoutSession ws = WorkoutSession.builder().id(20L).user(user).build();
        when(workoutService.findById(20L)).thenReturn(Optional.of(ws));

        commandRouter.rotearCallback(user, "edit_ex:20:ex123", 850, USER_ID, sender);
        verify(stateManager).putState(USER_ID, "AWAITING_EDIT_EXERCISE:20:ex123:850");
        verify(sender).enviarMensagemMarkdown(eq(USER_ID), contains("Editando exercício"), any(InlineKeyboardMarkup.class));

        when(stateManager.getState(USER_ID)).thenReturn("AWAITING_EDIT_EXERCISE:20:ex123:850");
        commandRouter.rotearEstadoTexto(user, "Supino 4x8 90kg", 15, USER_ID, sender);
        verify(workoutHandler).atualizarExercicioEditado(20L, "ex123", 850, "Supino 4x8 90kg", null, USER_ID, sender);
    }

    @Test
    void rotearCallback_confirmGoalERelatorioNavegacaoECancelState() {
        commandRouter.rotearCallback(user, "confirm_goal:2100:160:210:65", 900, USER_ID, sender);
        verify(stateManager).removeState(USER_ID);
        verify(goalHandler).confirmarMetasSugeridas(user, 2100, 160, 210, 65, 900, USER_ID, sender);

        LocalDate date = LocalDate.of(2026, 11, 18);
        commandRouter.rotearCallback(user, "open_report:2026-11-18", 901, USER_ID, sender);
        verify(reportHandler).abrirRelatorioPorData(user, date, USER_ID, sender);

        commandRouter.rotearCallback(user, "rep_nav:2026-11-18", 902, USER_ID, sender);
        verify(reportHandler).navegarRelatorio(user, date, 902, USER_ID, sender);

        commandRouter.rotearCallback(user, "rep_manage:2026-11-18", 903, USER_ID, sender);
        verify(reportHandler).abrirModoGerenciar(user, date, 903, USER_ID, sender);

        commandRouter.rotearCallback(user, "rep_view:2026-11-18", 904, USER_ID, sender);
        verify(reportHandler).voltarModoVisualizacao(user, date, 904, USER_ID, sender);

        commandRouter.rotearCallback(user, "cancel_state", 905, USER_ID, sender);
        verify(stateManager, times(2)).removeState(USER_ID);
        verify(sender).editarMensagemBot(eq(USER_ID), eq(905), contains("cancelada"), isNull());
    }

    @Test
    void rotearCallback_delRepMeal_deveExcluirEAtualizarRelatorio() {
        Meal meal = Meal.builder()
                .id(10L)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();
        when(mealService.findById(10L)).thenReturn(Optional.of(meal));

        commandRouter.rotearCallback(user, "del_rep_m:10", 99, USER_ID, sender);
        verify(mealService).deleteMeal(10L);
        verify(reportHandler).atualizarRelatorioAposExclusao(eq(user), any(LocalDate.class), eq(99), eq(USER_ID), eq(sender));
    }

    @Test
    void rotearCallback_delRepWorkout_deveExcluirEAtualizarRelatorio() {
        WorkoutSession ws = WorkoutSession.builder()
                .id(20L)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();
        when(workoutService.findById(20L)).thenReturn(Optional.of(ws));

        commandRouter.rotearCallback(user, "del_rep_w:20", 99, USER_ID, sender);
        verify(workoutService).deleteWorkout(20L);
        verify(reportHandler).atualizarRelatorioAposExclusao(eq(user), any(LocalDate.class), eq(99), eq(USER_ID), eq(sender));
    }

    @Test
    void rotearComando_aprovar_apenasAdmin_deveAprovarUsuario() {
        UserTelegram targetUser = UserTelegram.builder().id(555L).firstName("Fulano").build();
        when(userService.approveUser(555L)).thenReturn(Optional.of(targetUser));
        when(messageFormatter.escapeMarkdown("Fulano")).thenReturn("Fulano");

        commandRouter.rotearComando(user, "/aprovar 555", 1, ADMIN_ID, sender);
        verify(userService).approveUser(555L);
        verify(sender).enviarMensagemMarkdown(eq(ADMIN_ID), contains("Usuário Aprovado"));
    }

    @Test
    void rotearComando_aprovar_naoAdmin_deveIgnorarComando() {
        commandRouter.rotearComando(user, "/aprovar 555", 1, USER_ID, sender);
        verify(sender).enviarMensagem(eq(USER_ID), contains("Comando não reconhecido"));
        verify(userService, never()).approveUser(anyLong());
    }

    @Test
    void rotearComando_comandoComSufixoPlural_naoDeveReconhecerComoComandoValido() {
        commandRouter.rotearComando(user, "/refeicoes", 1, USER_ID, sender);
        commandRouter.rotearComando(user, "/treinos", 2, USER_ID, sender);
        verify(sender, times(2)).enviarMensagem(eq(USER_ID), contains("Comando não reconhecido"));
        verifyNoInteractions(mealHandler);
        verifyNoInteractions(workoutHandler);
    }

    @Test
    void rotearComando_comandoComMencaoAoBot_deveExtrairArgumentoCorretamente() {
        commandRouter.rotearComando(user, "/refeicao@ShapeLogBot Pão e ovo", 1, USER_ID, sender);
        verify(mealHandler).registrarRefeicao(eq(user), eq("Pão e ovo"), isNull(), eq(1), eq(USER_ID), eq(sender));
    }

    @Test
    void rotearCallback_deleteExComUuid_deveChamarWorkoutHandlerComExerciseId() {
        WorkoutSession ws = WorkoutSession.builder().id(20L).user(user).build();
        when(workoutService.findById(20L)).thenReturn(Optional.of(ws));

        commandRouter.rotearCallback(user, "delete_ex:20:a1b2c3d4", 99, USER_ID, sender);
        verify(workoutHandler).processarExclusaoExercicio(20L, "a1b2c3d4", 99, USER_ID, sender);
    }
}
