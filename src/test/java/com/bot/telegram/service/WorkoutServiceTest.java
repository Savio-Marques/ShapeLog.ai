package com.bot.telegram.service;

import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorkoutServiceTest {

    private WorkoutSessionRepository workoutRepository;
    private GeminiService geminiService;
    private ObjectMapper objectMapper;
    private WorkoutService workoutService;

    private UserTelegram testUser;

    @BeforeEach
    void setUp() {
        workoutRepository = mock(WorkoutSessionRepository.class);
        geminiService = mock(GeminiService.class);
        objectMapper = new ObjectMapper();
        workoutService = new WorkoutService(geminiService, workoutRepository, objectMapper, new SimpleMeterRegistry());

        testUser = UserTelegram.builder()
                .id(500L)
                .firstName("Sávio")
                .approved(true)
                .build();
    }

    @Test
    void createOrUpdateDraftWorkout_novoTreino_deveCriarComTituloEListaVazia() {
        when(workoutRepository.findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(eq(testUser), any(), any()))
                .thenReturn(Optional.empty());
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> {
            WorkoutSession ws = inv.getArgument(0);
            ws.setId(1L);
            return ws;
        });

        WorkoutSession session = workoutService.createOrUpdateDraftWorkout(testUser, "Peito e Tríceps", 101);
        assertNotNull(session);
        assertEquals(1L, session.getId());
        assertEquals("Peito e Tríceps", session.getRawInput());
        assertEquals("[]", session.getExercisesJson());
        assertEquals(101, session.getUserMessageId());
        verify(workoutRepository).save(any(WorkoutSession.class));
    }

    @Test
    void createOrUpdateDraftWorkout_treinoJaExistenteHoje_deveAtualizarTitulo() {
        WorkoutSession existente = WorkoutSession.builder()
                .id(2L)
                .user(testUser)
                .rawInput("Costas")
                .exercisesJson("[]")
                .build();

        when(workoutRepository.findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(eq(testUser), any(), any()))
                .thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutSession session = workoutService.createOrUpdateDraftWorkout(testUser, "Costas e Bíceps", 102);
        assertEquals(2L, session.getId());
        assertEquals("Costas e Bíceps", session.getRawInput());
        assertEquals(102, session.getUserMessageId());
    }

    @Test
    void editExerciseById_semExerciciosRetornados_deveLancarExcecao() {
        String jsonOriginal = "[{\"id\":\"ex-1\",\"name\":\"Supino\",\"series\":[]}]";
        WorkoutSession existente = WorkoutSession.builder()
                .id(15L)
                .user(testUser)
                .exercisesJson(jsonOriginal)
                .build();
        when(workoutRepository.findById(15L)).thenReturn(Optional.of(existente));

        WorkoutDto dtoVazio = WorkoutDto.builder().exercises(List.of()).build();
        when(geminiService.parseWorkout(any(), any())).thenReturn(dtoVazio);

        assertThrows(IllegalArgumentException.class, () ->
                workoutService.editExerciseById(15L, "ex-1", "texto sem exercicio", null));
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void deleteWorkout_deveChamarRepositoryDeleteById() {
        workoutService.deleteWorkout(30L);
        verify(workoutRepository).deleteById(30L);
    }

    @Test
    void deserializeExercises_nuloOuVazio_deveRetornarListaVazia() {
        List<WorkoutDto.ExerciseDto> vazia = workoutService.deserializeExercises("");
        assertNotNull(vazia);
        assertTrue(vazia.isEmpty());

        List<WorkoutDto.ExerciseDto> nulo = workoutService.deserializeExercises(null);
        assertNotNull(nulo);
        assertTrue(nulo.isEmpty());
    }

    @Test
    void deserializeExercises_jsonInvalido_deveLancarExcecaoSemSilenciar() {
        assertThrows(IllegalStateException.class, () ->
                workoutService.deserializeExercises("{jsonQuebrado}"));
    }

    @Test
    void addExercisesOrCreateWorkout_deveChamarGeminiForaDaTransacao() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        WorkoutService serviceComTx = new WorkoutService(geminiService, workoutRepository, objectMapper, new SimpleMeterRegistry(), txTemplate);

        WorkoutDto.SeriesDto series = WorkoutDto.SeriesDto.builder().reps(10).weight(80.0).build();
        WorkoutDto.ExerciseDto ex = WorkoutDto.ExerciseDto.builder().name("Supino Reto").series(List.of(series)).build();
        WorkoutDto dto = WorkoutDto.builder().description("Peito").exercises(List.of(ex)).build();

        when(geminiService.parseWorkout("supino 10 reps 80kg", null)).thenReturn(dto);
        when(workoutRepository.findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(eq(testUser), any(), any()))
                .thenReturn(Optional.empty());
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> {
            WorkoutSession ws = inv.getArgument(0);
            ws.setId(55L);
            return ws;
        });

        WorkoutService.WorkoutUpdateResult result = serviceComTx.addExercisesOrCreateWorkout(testUser, "supino 10 reps 80kg", null, 12);
        assertNotNull(result);

        InOrder inOrder = inOrder(geminiService, txManager, workoutRepository);
        inOrder.verify(geminiService).parseWorkout("supino 10 reps 80kg", null);
        inOrder.verify(txManager).getTransaction(any());
        inOrder.verify(workoutRepository).save(any(WorkoutSession.class));
        inOrder.verify(txManager).commit(any());
    }

    @Test
    void deserializeExercises_jsonValido_deveRetornarObjetos() {
        String json = "[{\"name\":\"Agachamento\",\"series\":[{\"reps\":8,\"weight\":100.0}]}]";
        List<WorkoutDto.ExerciseDto> list = workoutService.deserializeExercises(json);
        assertEquals(1, list.size());
        assertEquals("Agachamento", list.get(0).getName());
        assertEquals(1, list.get(0).getSeries().size());
        assertEquals(100.0, list.get(0).getSeries().get(0).getWeight());
    }

    @Test
    void getWorkoutsForDate_deveRetornarApenasDoPeriodo() {
        WorkoutSession ws = WorkoutSession.builder().id(1L).rawInput("Treino Hoje").build();
        when(workoutRepository.findByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(eq(testUser), any(), any()))
                .thenReturn(List.of(ws));

        List<WorkoutSession> list = workoutService.getWorkoutsForDate(testUser, LocalDate.now());
        assertEquals(1, list.size());
        assertEquals("Treino Hoje", list.get(0).getRawInput());
    }

    @Test
    void removeExerciseById_aposExcluirPrimeiro_deveEncontrarSegundoPeloIdSemIndexShift() {
        String jsonOriginal = "[{\"id\":\"ex-1\",\"name\":\"Supino\",\"series\":[]},{\"id\":\"ex-2\",\"name\":\"Crucifixo\",\"series\":[]},{\"id\":\"ex-3\",\"name\":\"Tríceps\",\"series\":[]}]";
        WorkoutSession existente = WorkoutSession.builder()
                .id(25L)
                .user(testUser)
                .exercisesJson(jsonOriginal)
                .build();

        when(workoutRepository.findById(25L)).thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        workoutService.removeExerciseById(25L, "ex-1");
        assertFalse(existente.getExercisesJson().contains("Supino"));
        assertTrue(existente.getExercisesJson().contains("Crucifixo"));
        assertTrue(existente.getExercisesJson().contains("Tríceps"));

        workoutService.removeExerciseById(25L, "ex-2");
        assertFalse(existente.getExercisesJson().contains("Crucifixo"));
        assertTrue(existente.getExercisesJson().contains("Tríceps"));
    }

    @Test
    void editExerciseById_aposExcluirPrimeiro_deveEditarExercicioCorretoPeloId() {
        String jsonOriginal = "[{\"id\":\"ex-1\",\"name\":\"Supino\",\"series\":[]},{\"id\":\"ex-2\",\"name\":\"Crucifixo\",\"series\":[]}]";
        WorkoutSession existente = WorkoutSession.builder()
                .id(26L)
                .user(testUser)
                .exercisesJson(jsonOriginal)
                .build();

        when(workoutRepository.findById(26L)).thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        workoutService.removeExerciseById(26L, "ex-1");

        WorkoutDto.SeriesDto series = WorkoutDto.SeriesDto.builder().reps(12).weight(22.0).build();
        WorkoutDto.ExerciseDto editedEx = WorkoutDto.ExerciseDto.builder().name("Crucifixo Inclinado").series(List.of(series)).build();
        WorkoutDto dto = WorkoutDto.builder().exercises(List.of(editedEx)).build();
        when(geminiService.parseWorkout(eq("crucifixo inclinado 12 reps 22kg"), isNull())).thenReturn(dto);

        WorkoutSession updated = workoutService.editExerciseById(26L, "ex-2", "crucifixo inclinado 12 reps 22kg", null);
        assertTrue(updated.getExercisesJson().contains("Crucifixo Inclinado"));
        assertTrue(updated.getExercisesJson().contains("ex-2"));
    }

    @Test
    void createNewDraftWorkout_deveCriarNovaSessaoMesmoComTreinoExistenteHoje() {
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> {
            WorkoutSession ws = inv.getArgument(0);
            ws.setId(99L);
            return ws;
        });

        WorkoutSession created = workoutService.createNewDraftWorkout(testUser, "Cardio", 301);
        assertNotNull(created);
        assertEquals(99L, created.getId());
        assertEquals("Cardio", created.getRawInput());
        assertEquals("[]", created.getExercisesJson());
        verify(workoutRepository, never()).findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void addExercisesOrCreateWorkout_semTreinoHoje_deveCriarSessaoComTituloDaIA() {
        WorkoutDto.SeriesDto series = WorkoutDto.SeriesDto.builder().reps(10).weight(70.0).build();
        WorkoutDto.ExerciseDto ex = WorkoutDto.ExerciseDto.builder().name("Supino Inclinado").series(List.of(series)).build();
        WorkoutDto dto = WorkoutDto.builder().description("Peito").durationMinutes(25).exercises(List.of(ex)).build();

        when(geminiService.parseWorkout(eq("supino inclinado 10 reps 70kg"), isNull())).thenReturn(dto);
        when(workoutRepository.findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(eq(testUser), any(), any()))
                .thenReturn(Optional.empty());
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> {
            WorkoutSession ws = inv.getArgument(0);
            ws.setId(77L);
            return ws;
        });

        WorkoutService.WorkoutUpdateResult result = workoutService.addExercisesOrCreateWorkout(testUser, "supino inclinado 10 reps 70kg", null, 401);
        assertTrue(result.createdNewSession);
        assertEquals("Peito", result.session.getRawInput());
        assertEquals(25, result.session.getDurationMinutes());
        assertEquals(1, result.addedExercises.size());
    }

    @Test
    void addExercisesOrCreateWorkout_comTreinoHoje_deveReutilizarSessaoExistente() {
        WorkoutSession existente = WorkoutSession.builder()
                .id(88L)
                .user(testUser)
                .rawInput("Treino A")
                .exercisesJson("[]")
                .build();

        WorkoutDto.SeriesDto series = WorkoutDto.SeriesDto.builder().reps(12).weight(50.0).build();
        WorkoutDto.ExerciseDto ex = WorkoutDto.ExerciseDto.builder().name("Puxada Alta").series(List.of(series)).build();
        WorkoutDto dto = WorkoutDto.builder().description("Costas").exercises(List.of(ex)).build();

        when(geminiService.parseWorkout(eq("puxada alta 12 reps 50kg"), isNull())).thenReturn(dto);
        when(workoutRepository.findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(eq(testUser), any(), any()))
                .thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutService.WorkoutUpdateResult result = workoutService.addExercisesOrCreateWorkout(testUser, "puxada alta 12 reps 50kg", null, 402);
        assertFalse(result.createdNewSession);
        assertEquals(88L, result.session.getId());
        assertEquals("Treino A", result.session.getRawInput());
    }

    @Test
    void addExercisesOrCreateWorkout_iaSemExercicios_naoDeveCriarSessaoNoBanco() {
        WorkoutDto dtoVazio = WorkoutDto.builder().description("Nada").exercises(List.of()).build();
        when(geminiService.parseWorkout(any(), any())).thenReturn(dtoVazio);

        assertThrows(IllegalArgumentException.class, () ->
                workoutService.addExercisesOrCreateWorkout(testUser, "texto inválido", null, 403));
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void deserializeExercises_comDetailsERetrocompatibilidadeLegado_deveFuncionar() {
        String jsonNovo = "[{\"id\":\"c1\",\"name\":\"Corrida\",\"series\":[],\"details\":[\"Distância: 10 km\",\"Tempo: 52 min\"]}]";
        List<WorkoutDto.ExerciseDto> novo = workoutService.deserializeExercises(jsonNovo);
        assertEquals(1, novo.size());
        assertEquals("Corrida", novo.get(0).getName());
        assertEquals(List.of("Distância: 10 km", "Tempo: 52 min"), novo.get(0).getDetails());

        String jsonLegado = "[{\"name\":\"Agachamento\",\"series\":[{\"reps\":8,\"weight\":100.0}],\"campoDesconhecido\":123}]";
        List<WorkoutDto.ExerciseDto> legado = workoutService.deserializeExercises(jsonLegado);
        assertEquals(1, legado.size());
        assertEquals("Agachamento", legado.get(0).getName());
        assertNull(legado.get(0).getDetails());
    }
}
