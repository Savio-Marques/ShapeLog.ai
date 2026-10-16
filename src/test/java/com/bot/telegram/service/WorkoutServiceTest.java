package com.bot.telegram.service;

import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        when(workoutRepository.findFirstByUserAndCreatedAtBetweenOrderByCreatedAtDesc(eq(testUser), any(), any()))
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

        when(workoutRepository.findFirstByUserAndCreatedAtBetweenOrderByCreatedAtDesc(eq(testUser), any(), any()))
                .thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutSession session = workoutService.createOrUpdateDraftWorkout(testUser, "Costas e Bíceps", 102);
        assertEquals(2L, session.getId());
        assertEquals("Costas e Bíceps", session.getRawInput());
        assertEquals(102, session.getUserMessageId());
    }

    @Test
    void addExercisesToDraft_exerciciosValidos_deveAdicionarAoJson() {
        WorkoutSession existente = WorkoutSession.builder()
                .id(10L)
                .user(testUser)
                .rawInput("Treino A")
                .exercisesJson("[]")
                .durationMinutes(30)
                .build();

        when(workoutRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutDto.SeriesDto series = WorkoutDto.SeriesDto.builder().reps(10).weight(80.0).build();
        WorkoutDto.ExerciseDto exercise = WorkoutDto.ExerciseDto.builder().name("Supino Reto").series(List.of(series)).build();
        WorkoutDto dto = WorkoutDto.builder().durationMinutes(20).exercises(List.of(exercise)).build();

        when(geminiService.parseWorkout(eq("supino 10 reps 80kg"), isNull())).thenReturn(dto);

        WorkoutService.WorkoutUpdateResult result = workoutService.addExercisesToDraft(10L, "supino 10 reps 80kg", null, 201);
        assertNotNull(result);
        assertEquals(0, result.startIndex);
        assertEquals(1, result.addedExercises.size());
        assertEquals("Supino Reto", result.addedExercises.get(0).getName());
        assertEquals(50, result.session.getDurationMinutes());
        assertTrue(result.session.getExercisesJson().contains("Supino Reto"));
    }

    @Test
    void addExercisesToDraft_treinoInexistente_deveLancarExcecao() {
        when(workoutRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                workoutService.addExercisesToDraft(999L, "supino 3x10", null, 1));
    }

    @Test
    void addExercisesToDraft_semExerciciosRetornados_deveLancarExcecao() {
        WorkoutSession existente = WorkoutSession.builder().id(10L).user(testUser).exercisesJson("[]").build();
        when(workoutRepository.findById(10L)).thenReturn(Optional.of(existente));

        WorkoutDto dtoVazio = WorkoutDto.builder().exercises(new ArrayList<>()).build();
        when(geminiService.parseWorkout(any(), any())).thenReturn(dtoVazio);

        assertThrows(IllegalArgumentException.class, () ->
                workoutService.addExercisesToDraft(10L, "texto aleatorio", null, 1));
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void editExercise_exercicioValido_deveSubstituirNoIndice() {
        String jsonOriginal = "[{\"name\":\"Supino Inclinado\",\"series\":[{\"reps\":10,\"weight\":60.0}]}]";
        WorkoutSession existente = WorkoutSession.builder()
                .id(15L)
                .user(testUser)
                .exercisesJson(jsonOriginal)
                .build();

        when(workoutRepository.findById(15L)).thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutDto.SeriesDto series = WorkoutDto.SeriesDto.builder().reps(12).weight(70.0).build();
        WorkoutDto.ExerciseDto exercise = WorkoutDto.ExerciseDto.builder().name("Supino Reto").series(List.of(series)).build();
        WorkoutDto dto = WorkoutDto.builder().exercises(List.of(exercise)).build();

        when(geminiService.parseWorkout(eq("supino reto 12 reps 70kg"), isNull())).thenReturn(dto);

        WorkoutSession updated = workoutService.editExercise(15L, 0, "supino reto 12 reps 70kg", null);
        assertNotNull(updated);
        assertTrue(updated.getExercisesJson().contains("Supino Reto"));
        assertFalse(updated.getExercisesJson().contains("Supino Inclinado"));
    }

    @Test
    void editExercise_semExerciciosRetornados_deveLancarExcecao() {
        WorkoutSession existente = WorkoutSession.builder().id(15L).user(testUser).exercisesJson("[]").build();
        when(workoutRepository.findById(15L)).thenReturn(Optional.of(existente));

        WorkoutDto dtoVazio = WorkoutDto.builder().exercises(List.of()).build();
        when(geminiService.parseWorkout(any(), any())).thenReturn(dtoVazio);

        assertThrows(IllegalArgumentException.class, () ->
                workoutService.editExercise(15L, 0, "texto sem exercicio", null));
        verify(workoutRepository, never()).save(any());
    }

    @Test
    void removeExercise_indiceValido_deveRemoverDoJson() {
        String jsonOriginal = "[{\"name\":\"Exercicio 1\",\"series\":[]},{\"name\":\"Exercicio 2\",\"series\":[]}]";
        WorkoutSession existente = WorkoutSession.builder()
                .id(20L)
                .user(testUser)
                .exercisesJson(jsonOriginal)
                .build();

        when(workoutRepository.findById(20L)).thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutSession updated = workoutService.removeExercise(20L, 0);
        assertFalse(updated.getExercisesJson().contains("Exercicio 1"));
        assertTrue(updated.getExercisesJson().contains("Exercicio 2"));
    }

    @Test
    void removeExercise_indiceInvalido_naoDeveAlterarLista() {
        String jsonOriginal = "[{\"name\":\"Exercicio 1\",\"series\":[]}]";
        WorkoutSession existente = WorkoutSession.builder()
                .id(20L)
                .user(testUser)
                .exercisesJson(jsonOriginal)
                .build();

        when(workoutRepository.findById(20L)).thenReturn(Optional.of(existente));
        when(workoutRepository.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkoutSession updated = workoutService.removeExercise(20L, 99);
        assertTrue(updated.getExercisesJson().contains("Exercicio 1"));
    }

    @Test
    void deleteWorkout_deveChamarRepositoryDeleteById() {
        workoutService.deleteWorkout(30L);
        verify(workoutRepository).deleteById(30L);
    }

    @Test
    void deserializeExercises_jsonInvalidoOuVazio_deveRetornarListaVazia() {
        List<WorkoutDto.ExerciseDto> vazia = workoutService.deserializeExercises("");
        assertNotNull(vazia);
        assertTrue(vazia.isEmpty());

        List<WorkoutDto.ExerciseDto> nulo = workoutService.deserializeExercises(null);
        assertNotNull(nulo);
        assertTrue(nulo.isEmpty());

        List<WorkoutDto.ExerciseDto> invalido = workoutService.deserializeExercises("{jsonQuebrado}");
        assertNotNull(invalido);
        assertTrue(invalido.isEmpty());
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
        when(workoutRepository.findByUserAndCreatedAtBetweenOrderByIdAsc(eq(testUser), any(), any()))
                .thenReturn(List.of(ws));

        List<WorkoutSession> list = workoutService.getWorkoutsForDate(testUser, LocalDate.now());
        assertEquals(1, list.size());
        assertEquals("Treino Hoje", list.get(0).getRawInput());
    }

    @Test
    void saveBotMessageId_deveSalvarQuandoEncontrado() {
        WorkoutSession ws = WorkoutSession.builder().id(10L).build();
        when(workoutRepository.findById(10L)).thenReturn(Optional.of(ws));

        workoutService.saveBotMessageId(10L, 999);
        assertEquals(999, ws.getBotMessageId());
        verify(workoutRepository).save(ws);
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
}
