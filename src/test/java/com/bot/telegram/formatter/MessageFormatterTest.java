package com.bot.telegram.formatter;

import com.bot.telegram.dto.DailyReportDto;
import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.service.IWorkoutService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageFormatterTest {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    private IWorkoutService workoutService;
    private MessageFormatter messageFormatter;

    @BeforeEach
    void setUp() {
        workoutService = mock(IWorkoutService.class);
        messageFormatter = new MessageFormatter(workoutService, APP_ZONE);
    }

    @Test
    void formatStart_deveConterNomeDoUsuario() {
        String result = messageFormatter.formatStart("Sávio");
        assertNotNull(result);
        assertTrue(result.contains("Sávio"));
    }

    @Test
    void formatStart_deveConterTodosOsComandosEExemplosEmBlockquote() {
        String result = messageFormatter.formatStart("Sávio");
        assertTrue(result.contains("/refeicao"));
        assertTrue(result.contains("/treino"));
        assertTrue(result.contains("/exercicio"));
        assertTrue(result.contains("/meta"));
        assertTrue(result.contains("/relatorio"));
        assertTrue(result.contains("> *Exemplos"));
    }

    @Test
    void formatGoalsUpdated_deveFormatarValoresCorretamente() {
        String result = messageFormatter.formatGoalsUpdated(2500, 180, 250, 70);
        assertNotNull(result);
        assertTrue(result.contains("2.500 kcal"));
        assertTrue(result.contains("180g"));
        assertTrue(result.contains("250g"));
        assertTrue(result.contains("70g"));
    }

    @Test
    void formatMealRegistered_deveExibirDescricaoEMacrosSemDecimalDesnecessario() {
        Meal meal = Meal.builder()
                .description("Arroz, feijão e frango")
                .calories(600)
                .protein(45.5)
                .carbs(60.0)
                .fat(12.0)
                .build();

        String result = messageFormatter.formatMealRegistered(meal);
        assertNotNull(result);
        assertTrue(result.contains("Arroz, feijão e frango"));
        assertTrue(result.contains("600 kcal"));
        assertTrue(result.contains("P 45,5g"));
        assertTrue(result.contains("C 60g"));
        assertTrue(result.contains("G 12g"));
    }

    @Test
    void formatMealRegistered_deveTratarValoresNulos() {
        Meal meal = Meal.builder().build();
        String result = messageFormatter.formatMealRegistered(meal);
        assertNotNull(result);
        assertTrue(result.contains("sem descrição"));
        assertTrue(result.contains("0 kcal"));
    }

    @Test
    void formatDailyReport_deveMostrarHojeParaDataAtual() {
        UserTelegram user = UserTelegram.builder().firstName("Sávio").build();
        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of())
                .workouts(List.of())
                .user(user)
                .build();

        String result = messageFormatter.formatDailyReport(report, LocalDate.now(APP_ZONE));
        assertTrue(result.contains("Relatório · Hoje"));
    }

    @Test
    void formatDailyReport_deveMostrarOntemParaDataAnterior() {
        UserTelegram user = UserTelegram.builder().firstName("Sávio").build();
        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of())
                .workouts(List.of())
                .user(user)
                .build();

        String result = messageFormatter.formatDailyReport(report, LocalDate.now(APP_ZONE).minusDays(1));
        assertTrue(result.contains("Relatório · Ontem"));
    }

    @Test
    void escapeMarkdown_deveEscaparCaracteresEspeciais() {
        String input = "Olá! Teste_1 * 2 [3] (4) ~ > # + - = | { } . !";
        String escaped = messageFormatter.escapeMarkdown(input);
        assertNotNull(escaped);
        assertTrue(escaped.contains("\\!"));
        assertTrue(escaped.contains("\\_"));
        assertTrue(escaped.contains("\\*"));
        assertTrue(escaped.contains("\\["));
        assertTrue(escaped.contains("\\]"));
        assertTrue(escaped.contains("\\."));
        assertTrue(escaped.contains("\\>"));
    }

    @Test
    void testMessageFormatterDailyReportMigrado() throws Exception {
        when(workoutService.deserializeExercises(anyString())).thenAnswer(inv -> {
            String json = inv.getArgument(0);
            if (json == null || json.isBlank() || json.equals("[]")) return List.of();
            ObjectMapper mapper = new ObjectMapper();
            return java.util.Arrays.asList(mapper.readValue(json, WorkoutDto.ExerciseDto[].class));
        });

        UserTelegram user = UserTelegram.builder()
                .firstName("Sávio")
                .targetCalories(2600)
                .targetProtein(191)
                .targetCarbs(200)
                .targetFat(82)
                .build();

        Meal meal = Meal.builder()
                .description("uma maçã média, 4 bananas")
                .calories(515)
                .protein(5.7)
                .carbs(133.0)
                .fat(1.9)
                .build();

        String exercisesJson = "[{\"name\":\"Supino Inclinado\",\"series\":[{\"reps\":12,\"weight\":80.0},{\"reps\":8,\"weight\":100.0}]},{\"name\":\"Supino Reto\",\"series\":[{\"reps\":10,\"weight\":40.0}]}]";

        WorkoutSession workout = WorkoutSession.builder()
                .rawInput("Peito")
                .exercisesJson(exercisesJson)
                .build();

        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of(meal))
                .workouts(List.of(workout))
                .totalCalories(2752)
                .totalProtein(344.5)
                .totalCarbs(184.5)
                .totalFat(64.5)
                .user(user)
                .build();

        String formatted = messageFormatter.formatDailyReport(report);
        assertNotNull(formatted);
        assertTrue(formatted.contains("`██████████` 106%"));
        assertTrue(formatted.contains("Peito"));
        assertTrue(formatted.contains("Supino Inclinado"));
        assertTrue(formatted.contains("12×80 · 8×100 kg"));
        assertTrue(formatted.contains("1×10 · 40 kg"));
        assertTrue(formatted.indexOf("Calorias") < formatted.indexOf("Refeições"));
        assertFalse(formatted.contains("🏋️‍♂️"));
    }

    @Test
    void formatMealRegistered_comResumoDiarioEFlagEditada_deveExibirProgressoESaldo() {
        UserTelegram user = UserTelegram.builder()
                .targetCalories(2000)
                .targetProtein(150)
                .targetCarbs(200)
                .targetFat(60)
                .build();
        Meal meal = Meal.builder()
                .description("4 ovos mexidos (~200g)")
                .calories(400)
                .protein(28.0)
                .carbs(4.0)
                .fat(28.0)
                .build();
        DailyReportDto report = DailyReportDto.builder()
                .user(user)
                .meals(List.of(meal))
                .workouts(List.of())
                .totalCalories(1400)
                .totalProtein(110.0)
                .totalCarbs(140.0)
                .totalFat(45.0)
                .build();

        String result = messageFormatter.formatMealRegistered(meal, report, LocalDate.now(APP_ZONE), true);
        assertTrue(result.contains("editada"));
        assertTrue(result.contains("> 🎯 *Progresso · Hoje"));
        assertTrue(result.contains("`1.400 / 2.000 kcal`"));
        assertTrue(result.contains("faltam 600"));
        assertTrue(result.contains("`110 / 150g`"));
        assertTrue(result.contains("faltam 40g"));
    }

    @Test
    void formatGoalsUpdated_comDivergenciaCalorica_deveExibirAviso() {
        String divergente = messageFormatter.formatGoalsUpdated(1000, 200, 200, 60);
        assertTrue(divergente.contains("Atenção"));

        String coerente = messageFormatter.formatGoalsUpdated(2350, 180, 250, 70);
        assertFalse(coerente.contains("Atenção"));
    }

    @Test
    void formatPendingInputPrompt_deveTratarTextoEVoz() {
        String textPrompt = messageFormatter.formatPendingInputPrompt("comi 2 ovos! (manhã)", false);
        assertTrue(textPrompt.contains("Mensagem recebida"));
        assertTrue(textPrompt.contains("> _\"comi 2 ovos\\! \\(manhã\\)\"_"));

        String voicePrompt = messageFormatter.formatPendingInputPrompt(null, true);
        assertTrue(voicePrompt.contains("Áudio recebido"));
    }

    @Test
    void formatCurrentGoalsESuggestion_devemFormatarComSeguranca() {
        UserTelegram user = UserTelegram.builder()
                .targetCalories(2200)
                .targetProtein(160)
                .targetCarbs(220)
                .targetFat(70)
                .build();
        String current = messageFormatter.formatCurrentGoals(user);
        assertTrue(current.contains("2.200 kcal"));
        assertTrue(current.contains("160g"));

        GoalDto dto = GoalDto.builder()
                .calories(2100)
                .protein(165)
                .carbs(210)
                .fat(65)
                .explanation("Déficit moderado para 80kg.")
                .build();
        String suggestion = messageFormatter.formatGoalSuggestion(dto);
        assertTrue(suggestion.contains("2.100 kcal"));
        assertTrue(suggestion.contains("Déficit moderado para 80kg\\."));
        assertTrue(suggestion.contains("nutricionista"));
    }

    @Test
    void formatExercisesAdded_cardioCrossfitHyrox_deveRenderizarDetailsSemSeries() {
        WorkoutDto.ExerciseDto esteira = WorkoutDto.ExerciseDto.builder()
                .name("Esteira")
                .series(List.of())
                .details(List.of("Duração: 30 min", "Velocidade: 10 km/h", "Inclinação: 3%"))
                .build();

        String card = messageFormatter.formatExercisesAdded(List.of(esteira), "Cardio");
        assertTrue(card.contains("🏋️ *Treino:*"));
        assertTrue(card.contains("> 🔹 *Esteira*"));
        assertTrue(card.contains("> • Duração: 30 min"));
        assertTrue(card.contains("> • Velocidade: 10 km/h"));
        assertTrue(card.contains("> • Inclinação: 3%"));
    }

    @Test
    void formatDailyReport_treinoCardioComDuracaoEDetails_deveExibirCabecalhoEDetalhes() throws Exception {
        when(workoutService.deserializeExercises(anyString())).thenAnswer(inv -> {
            String json = inv.getArgument(0);
            ObjectMapper mapper = new ObjectMapper();
            return java.util.Arrays.asList(mapper.readValue(json, WorkoutDto.ExerciseDto[].class));
        });

        UserTelegram user = UserTelegram.builder().firstName("Sávio").build();
        String json = "[{\"name\":\"WOD AMRAP 20 min\",\"series\":[],\"details\":[\"Movimentos: 5 pull-ups, 10 push-ups\",\"Resultado: 12 rounds\"]}]";
        WorkoutSession workout = WorkoutSession.builder()
                .rawInput("CrossFit")
                .durationMinutes(20)
                .exercisesJson(json)
                .build();
        DailyReportDto report = DailyReportDto.builder()
                .meals(List.of())
                .workouts(List.of(workout))
                .user(user)
                .build();

        String formatted = messageFormatter.formatDailyReport(report, LocalDate.now(APP_ZONE));
        assertTrue(formatted.contains("*CrossFit* · 20 min"));
        assertTrue(formatted.contains("WOD AMRAP 20 min"));
        assertTrue(formatted.contains("↳ Movimentos: 5 pull\\-ups, 10 push\\-ups"));
        assertTrue(formatted.contains("↳ Resultado: 12 rounds"));
    }

    @Test
    void formatSeriesCompact_deveCompactarCenariosComuns() {
        List<WorkoutDto.SeriesDto> uniformWithWeight = List.of(
                WorkoutDto.SeriesDto.builder().reps(10).weight(60.0).build(),
                WorkoutDto.SeriesDto.builder().reps(10).weight(60.0).build(),
                WorkoutDto.SeriesDto.builder().reps(10).weight(60.0).build(),
                WorkoutDto.SeriesDto.builder().reps(10).weight(60.0).build()
        );
        assertEquals("4×10 · 60 kg", messageFormatter.formatSeriesCompact(uniformWithWeight));

        List<WorkoutDto.SeriesDto> bodyweight = List.of(
                WorkoutDto.SeriesDto.builder().reps(15).weight(0.0).build(),
                WorkoutDto.SeriesDto.builder().reps(15).weight(0.0).build(),
                WorkoutDto.SeriesDto.builder().reps(15).weight(null).build()
        );
        assertEquals("3×15", messageFormatter.formatSeriesCompact(bodyweight));

        List<WorkoutDto.SeriesDto> sameWeightVariedReps = List.of(
                WorkoutDto.SeriesDto.builder().reps(12).weight(60.0).build(),
                WorkoutDto.SeriesDto.builder().reps(10).weight(60.0).build(),
                WorkoutDto.SeriesDto.builder().reps(8).weight(60.0).build()
        );
        assertEquals("12/10/8 reps · 60 kg", messageFormatter.formatSeriesCompact(sameWeightVariedReps));

        List<WorkoutDto.SeriesDto> progressiveWeights = List.of(
                WorkoutDto.SeriesDto.builder().reps(12).weight(40.0).build(),
                WorkoutDto.SeriesDto.builder().reps(10).weight(50.0).build(),
                WorkoutDto.SeriesDto.builder().reps(8).weight(60.0).build()
        );
        assertEquals("12×40 · 10×50 · 8×60 kg", messageFormatter.formatSeriesCompact(progressiveWeights));

        List<WorkoutDto.SeriesDto> mixedWeightAndBodyweight = List.of(
                WorkoutDto.SeriesDto.builder().reps(12).weight(20.0).build(),
                WorkoutDto.SeriesDto.builder().reps(15).weight(0.0).build()
        );
        assertEquals("12×20kg · 15 reps", messageFormatter.formatSeriesCompact(mixedWeightAndBodyweight));
    }

    @Test
    void formatDailyReport_comMuitosItens_deveRespeitarLimite4096EIntegridadeMarkdown() throws Exception {
        when(workoutService.deserializeExercises(anyString())).thenAnswer(inv -> {
            String json = inv.getArgument(0);
            ObjectMapper mapper = new ObjectMapper();
            return java.util.Arrays.asList(mapper.readValue(json, WorkoutDto.ExerciseDto[].class));
        });

        List<Meal> meals = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            meals.add(Meal.builder()
                    .description("Refeição detalhada número " + i + " com arroz integral, frango grelhado, azeite extra virgem e legumes variados no vapor")
                    .calories(550)
                    .protein(42.0)
                    .carbs(60.0)
                    .fat(15.0)
                    .build());
        }

        String exercisesJson = "[{\"name\":\"Supino reto barra\",\"series\":[{\"reps\":10,\"weight\":80.0},{\"reps\":10,\"weight\":80.0}]},"
                + "{\"name\":\"Crucifixo inclinado\",\"series\":[{\"reps\":12,\"weight\":20.0},{\"reps\":10,\"weight\":22.0}]},"
                + "{\"name\":\"Tríceps corda\",\"series\":[{\"reps\":15,\"weight\":25.0}]}]";
        List<WorkoutSession> workouts = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            workouts.add(WorkoutSession.builder()
                    .rawInput("Treino Completo Sessão " + i)
                    .durationMinutes(60)
                    .exercisesJson(exercisesJson)
                    .build());
        }

        DailyReportDto hugeReport = DailyReportDto.builder()
                .user(UserTelegram.builder().firstName("Sávio").targetCalories(2500).targetProtein(180).build())
                .meals(meals)
                .workouts(workouts)
                .totalCalories(16500)
                .totalProtein(1260.0)
                .totalCarbs(1800.0)
                .totalFat(450.0)
                .build();

        String formatted = messageFormatter.formatDailyReport(hugeReport, LocalDate.now(APP_ZONE));
        assertTrue(formatted.length() <= 4096, "Relatório excedeu 4096 chars: " + formatted.length());
        assertTrue(formatted.contains("omitida") || formatted.contains("omitido"));

        int unescapedBackticks = countUnescaped(formatted, '`');
        assertEquals(0, unescapedBackticks % 2, "Backticks desbalanceados após truncamento");
    }

    @Test
    void toBlockquote_comTextoMultilinhaEEspeciais_devePrefixarTodasLinhasComSeguranca() {
        String escaped = messageFormatter.escapeMarkdown("Linha 1!\n> citação (interna)\nLinha 3.");
        String quoted = messageFormatter.toBlockquote(escaped);
        String[] lines = quoted.split("\n");
        assertEquals(3, lines.length);
        assertEquals("> Linha 1\\!", lines[0]);
        assertEquals("> \\> citação \\(interna\\)", lines[1]);
        assertEquals("> Linha 3\\.", lines[2]);
    }

    private int countUnescaped(String text, char target) {
        int count = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == target) {
                count++;
            }
        }
        return count;
    }
}
