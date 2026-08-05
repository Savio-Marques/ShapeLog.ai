package com.bot.telegram.bot;

import com.bot.telegram.bot.handler.MealHandler;
import com.bot.telegram.bot.handler.ReportHandler;
import com.bot.telegram.bot.handler.WorkoutHandler;
import com.bot.telegram.formatter.MessageFormatter;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.IWorkoutService;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandRouterTest {

    private MealHandler mealHandler;
    private WorkoutHandler workoutHandler;
    private ReportHandler reportHandler;
    private MessageFormatter messageFormatter;
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
        messageFormatter = mock(MessageFormatter.class);
        mealService = mock(MealService.class);
        workoutService = mock(IWorkoutService.class);
        stateManager = mock(StateManager.class);
        userService = mock(UserService.class);
        sender = mock(BotActionSender.class);

        commandRouter = new CommandRouter(
                mealHandler, workoutHandler, reportHandler,
                messageFormatter, mealService, workoutService,
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
    void rotearComando_treinoComTexto_deveChamarWorkoutHandler() {
        commandRouter.rotearComando(user, "/treino Peito", 1, USER_ID, sender);
        verify(workoutHandler).criarRascunho(eq(user), eq("Peito"), eq(1), eq(USER_ID), eq(sender));
    }

    @Test
    void rotearComando_metaComValoresCorretos_deveSalvarNoBanco() {
        when(messageFormatter.formatGoalsUpdated(2000, 150, 200, 60)).thenReturn("Metas atualizadas!");
        commandRouter.rotearComando(user, "/meta 2000 150 200 60", 1, USER_ID, sender);
        verify(userService).updateGoals(user, 2000, 150, 200, 60);
        verify(sender).enviarMensagemMarkdown(USER_ID, "Metas atualizadas!");
    }

    @Test
    void rotearComando_metaSemValoresSuficientes_deveMostrarErro() {
        commandRouter.rotearComando(user, "/meta 2000", 1, USER_ID, sender);
        verify(sender).enviarMensagem(eq(USER_ID), contains("Formato incorreto"));
        verifyNoInteractions(userService);
    }

    @Test
    void rotearComando_comandoDesconhecido_deveMostrarListaDeComandos() {
        commandRouter.rotearComando(user, "/comandoinvalido", 1, USER_ID, sender);
        verify(sender).enviarMensagem(eq(USER_ID), contains("Comando não reconhecido"));
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
}
