package com.bot.telegram.service;

import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.dto.MealDto;
import com.bot.telegram.dto.WorkoutDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final long GEMINI_TIMEOUT_SECONDS = 45L;
    private static final int MAX_OUTPUT_TOKENS = 2048;

    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;
    private final Executor geminiExecutor;
    private final RateLimiterService rateLimiterService;

    public GeminiService(ChatModel chatModel, MeterRegistry meterRegistry) {
        this(chatModel, meterRegistry, Runnable::run, null);
    }

    public GeminiService(ChatModel chatModel, MeterRegistry meterRegistry, @Qualifier("geminiExecutor") Executor geminiExecutor) {
        this(chatModel, meterRegistry, geminiExecutor, null);
    }

    @Autowired
    public GeminiService(ChatModel chatModel, MeterRegistry meterRegistry,
                         @Qualifier("geminiExecutor") Executor geminiExecutor,
                         RateLimiterService rateLimiterService) {
        this.chatClient = ChatClient.create(chatModel);
        this.meterRegistry = meterRegistry;
        this.geminiExecutor = geminiExecutor;
        this.rateLimiterService = rateLimiterService;
    }

    public MealDto parseMeal(String text, byte[] audioBytes) {
        validateInput(text, audioBytes);
        String promptText = """
                Extraia os alimentos e estime os macronutrientes.
                Sempre que o usuário omitir o peso/volume exato ou usar medidas caseiras (ex: '1 prato', '1 concha', '1 bife', 'arroz e feijão'), inclua o peso ou volume estimado em gramas ou ml entre parênteses.
                Retorne um JSON com exatamente estes campos:
                - "description": liste cada alimento com sua quantidade/peso estimado em letras minúsculas separados por vírgula (ex: 'arroz branco (~150g), feijão carioca (~100g), peito de frango grelhado (~150g)')
                - "calories": total de calorias como número inteiro
                - "protein": proteínas em gramas como decimal
                - "carbs": carboidratos em gramas como decimal
                - "fat": gorduras em gramas como decimal
                """;
        if (hasAudio(audioBytes)) {
            return callAudioPrompt("meal", "Analise o áudio em anexo contendo o relato da refeição consumida.\n" + promptText, audioBytes, MealDto.class);
        }
        return callTextPrompt("meal", "Analise a refeição informada: \"" + text + "\".\n" + promptText, MealDto.class);
    }

    public MealDto parseMealUpdate(String previousDescription, String text, byte[] audioBytes) {
        validateInput(text, audioBytes);
        String original = (previousDescription != null && !previousDescription.isBlank()) ? previousDescription : "sem descrição";
        String instruction = """
                Refeição original registrada: "%s".
                Se o usuário enviar uma correção parcial (ajustar quantidade, adicionar, remover ou trocar um item), aplique a alteração mantendo os demais itens da refeição original.
                Se o usuário descrever uma refeição totalmente diferente, substitua por completo.
                Sempre que o peso/volume exato for omitido ou estiver em medidas caseiras, inclua a estimativa em gramas ou ml entre parênteses (ex: 'arroz branco (~150g)').
                Retorne um JSON atualizado com exatamente estes campos:
                - "description": liste cada alimento com sua quantidade/peso estimado em letras minúsculas separados por vírgula
                - "calories": total de calorias como número inteiro
                - "protein": proteínas em gramas como decimal
                - "carbs": carboidratos em gramas como decimal
                - "fat": gorduras em gramas como decimal
                """.formatted(original);

        if (hasAudio(audioBytes)) {
            return callAudioPrompt("meal", "Analise o áudio em anexo contendo uma correção ou substituição da refeição.\n" + instruction, audioBytes, MealDto.class);
        }
        return callTextPrompt("meal", "Solicitação do usuário para editar a refeição: \"" + text + "\".\n" + instruction, MealDto.class);
    }

    public WorkoutDto parseWorkout(String text, byte[] audioBytes) {
        validateInput(text, audioBytes);
        String instructions = """
                Extraia:
                - "description": modalidade ou grupamento muscular (ex: 'Peito', 'Costas', 'Cardio', 'Corrida', 'CrossFit', 'Hyrox')
                - "durationMinutes": duração total estimada em minutos como número inteiro (quando informada; caso contrário null)
                - "exercises": lista de exercícios ou blocos/estações realizados (deixe o campo "id" sempre null):
                  * Para musculação/força: preencha "name" (ex: 'Supino Inclinado') e "series" detalhando "reps" e "weight" (kg) individualmente para cada série (nunca agrupe séries com cargas ou repetições diferentes). Use "details" apenas se houver observação extra relevante (ex: 'Drop-set').
                  * Para cardio, corrida, CrossFit, Hyrox, natação, lutas, isometria ou outras modalidades: deixe "series" como lista vazia [] e preencha "details" com itens curtos no formato "Chave: Valor" (ex: ["Duração: 30 min", "Velocidade: 10 km/h"], ["Distância: 10 km", "Pace: 5:12/km"], ["Movimentos: 5 pull-ups, 10 push-ups", "Resultado: 12 rounds"]). Em circuitos ou Hyrox com múltiplas estações, separe cada estação em um item de "exercises".
                """;
        if (hasAudio(audioBytes)) {
            return callAudioPrompt("workout", "Analise o áudio em anexo contendo o relato do treino realizado.\n" + instructions, audioBytes, WorkoutDto.class);
        }
        return callTextPrompt("workout", "Analise o treino informado: \"" + text + "\".\n" + instructions, WorkoutDto.class);
    }

    public WorkoutDto parseExerciseUpdate(WorkoutDto.ExerciseDto previousExercise, String text, byte[] audioBytes) {
        validateInput(text, audioBytes);
        String originalSummary = formatExerciseContext(previousExercise);
        String instruction = """
                Exercício original registrado: %s.
                Se o usuário enviar uma correção parcial (alterar apenas nome, carga, repetições, quantidade de séries ou detalhes), aplique a alteração mantendo o restante das informações do exercício original.
                Se o usuário descrever um exercício totalmente diferente, substitua por completo.
                Retorne um JSON com o campo "exercises" contendo exatamente 1 exercício atualizado (deixe o campo "id" sempre null):
                - Para musculação/força: preencha "name" e "series" detalhando "reps" e "weight" (kg) individualmente para cada série.
                - Para cardio, corrida, CrossFit, Hyrox ou outras modalidades: deixe "series" como [] e preencha "details" com itens curtos no formato "Chave: Valor".
                """.formatted(originalSummary);
        if (hasAudio(audioBytes)) {
            return callAudioPrompt("workout", "Analise o áudio em anexo contendo uma correção ou substituição de exercício.\n" + instruction, audioBytes, WorkoutDto.class);
        }
        return callTextPrompt("workout", "Solicitação do usuário para editar o exercício: \"" + text + "\".\n" + instruction, WorkoutDto.class);
    }

    private String formatExerciseContext(WorkoutDto.ExerciseDto ex) {
        if (ex == null) {
            return "Exercício sem detalhes";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getName() != null && !ex.getName().isBlank() ? ex.getName().trim() : "Exercício");
        if (ex.getSeries() != null && !ex.getSeries().isEmpty()) {
            sb.append(" (séries: ");
            for (int i = 0; i < ex.getSeries().size(); i++) {
                WorkoutDto.SeriesDto s = ex.getSeries().get(i);
                if (i > 0) sb.append("; ");
                int reps = s != null && s.getReps() != null ? s.getReps() : 0;
                double weight = s != null && s.getWeight() != null ? s.getWeight() : 0.0;
                sb.append(i + 1).append("ª: ").append(reps).append(" reps, ").append(weight).append(" kg");
            }
            sb.append(")");
        }
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            sb.append(" [detalhes: ").append(String.join(", ", ex.getDetails())).append("]");
        }
        return sb.toString();
    }

    public GoalDto parseGoal(String text, byte[] audioBytes, int currentCal, int currentProt, int currentCarb, int currentFat) {
        validateInput(text, audioBytes);
        String instruction = """
                Metas atuais do usuário: %d kcal, %dg proteínas, %dg carboidratos, %dg gorduras.
                Calcule ou ajuste as metas diárias de macronutrientes com base na solicitação do usuário (seja um ajuste parcial nas metas atuais ou um cálculo completo por peso, altura, rotina e objetivo).
                Retorne um JSON com exatamente estes campos:
                - "calories": meta diária de calorias como número inteiro positivo
                - "protein": meta diária de proteínas em gramas como número inteiro positivo
                - "carbs": meta diária de carboidratos em gramas como número inteiro positivo
                - "fat": meta diária de gorduras em gramas como número inteiro positivo
                - "explanation": explicação curta em português (1 frase objetiva) justificando os valores sugeridos
                Garanta consistência energética aproximada onde 4*protein + 4*carbs + 9*fat ≈ calories.
                """.formatted(currentCal, currentProt, currentCarb, currentFat);

        if (hasAudio(audioBytes)) {
            return callAudioPrompt("goal", "Analise o áudio em anexo com o objetivo nutricional do usuário.\n" + instruction, audioBytes, GoalDto.class);
        }
        return callTextPrompt("goal", "Solicitação do usuário para definir metas: \"" + text + "\".\n" + instruction, GoalDto.class);
    }

    private void validateInput(String text, byte[] audioBytes) {
        if ((text == null || text.isBlank()) && !hasAudio(audioBytes)) {
            throw new IllegalArgumentException("Nenhum conteúdo para analisar. Envie um texto ou áudio válido.");
        }
    }

    private boolean hasAudio(byte[] audioBytes) {
        return audioBytes != null && audioBytes.length > 0;
    }

    private <T> T callAudioPrompt(String metricType, String promptText, byte[] audioBytes, Class<T> responseType) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
        String fullPrompt = promptText + System.lineSeparator() + converter.getFormat();
        ByteArrayResource resource = new ByteArrayResource(audioBytes);
        Media media = new Media(MimeType.valueOf("audio/ogg"), resource);
        UserMessage userMessage = UserMessage.builder()
                .text(fullPrompt)
                .media(media)
                .build();
        return executeStructuredCall(metricType, userMessage, converter);
    }

    private <T> T callTextPrompt(String metricType, String promptText, Class<T> responseType) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
        String fullPrompt = promptText + System.lineSeparator() + converter.getFormat();
        UserMessage userMessage = UserMessage.builder()
                .text(fullPrompt)
                .build();
        return executeStructuredCall(metricType, userMessage, converter);
    }

    private <T> T executeStructuredCall(String metricType, UserMessage userMessage, BeanOutputConverter<T> converter) {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .responseMimeType("application/json")
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                .build();
        Prompt prompt = new Prompt(userMessage, options);
        return executeWithMetrics(metricType, () -> {
            ChatResponse response = chatClient.prompt(prompt)
                    .call()
                    .chatResponse();
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                throw new RuntimeException("Resposta vazia da API do Gemini.");
            }
            StringBuilder sb = new StringBuilder();
            for (Generation gen : response.getResults()) {
                if (gen != null && gen.getOutput() != null && gen.getOutput().getText() != null) {
                    sb.append(gen.getOutput().getText());
                }
            }
            String fullText = sb.toString();
            try {
                return converter.convert(fullText);
            } catch (RuntimeException e) {
                String finishReason = response.getResult() != null && response.getResult().getMetadata() != null
                        ? String.valueOf(response.getResult().getMetadata().getFinishReason())
                        : "UNKNOWN";
                log.error("Falha ao converter JSON do Gemini (type={}, parts={}, finishReason={}, length={}): {}",
                        metricType, response.getResults().size(), finishReason, fullText.length(), fullText, e);
                throw e;
            }
        });
    }

    private <T> T executeWithMetrics(String metricType, Supplier<T> supplier) {
        if (rateLimiterService != null) {
            rateLimiterService.checkAllowed(parseMdcChatId());
        }
        meterRegistry.counter("shapelog.gemini.calls", "type", metricType).increment();
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            } else {
                MDC.clear();
            }
            try {
                return supplier.get();
            } finally {
                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
            }
        }, geminiExecutor);
        try {
            T result = future.get(GEMINI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sample.stop(meterRegistry.timer("shapelog.gemini.call.duration", "type", metricType));
            return result;
        } catch (Exception e) {
            future.cancel(true);
            meterRegistry.counter("shapelog.gemini.errors", "type", metricType).increment();
            throw new RuntimeException("Erro ao chamar API do Gemini (timeout ou falha): " + e.getMessage(), e);
        }
    }

    private Long parseMdcChatId() {
        String raw = MDC.get("chatId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
