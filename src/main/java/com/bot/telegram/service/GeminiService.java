package com.bot.telegram.service;

import com.bot.telegram.dto.GoalDto;
import com.bot.telegram.dto.MealDto;
import com.bot.telegram.dto.WorkoutDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class GeminiService {

    private static final long GEMINI_TIMEOUT_SECONDS = 45L;

    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;

    public GeminiService(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatClient = ChatClient.create(chatModel);
        this.meterRegistry = meterRegistry;
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
        return callTextPrompt("meal", "Analise a refeição informada: \"{text}\".\n" + promptText, text, MealDto.class);
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
        return callTextPrompt("meal", "Solicitação do usuário para editar a refeição: \"{text}\".\n" + instruction, text, MealDto.class);
    }

    public WorkoutDto parseWorkout(String text, byte[] audioBytes) {
        validateInput(text, audioBytes);
        if (hasAudio(audioBytes)) {
            String audioPrompt = "Analise o áudio em anexo contendo o relato do treino realizado. Extraia a descrição do treino (description como o grupamento muscular, ex: 'Peito', 'Costas', 'Pernas'), a duração estimada em minutos (durationMinutes) e a lista de exercícios (exercises). Cada exercício deve conter o nome do exercício (name, ex: 'Supino Inclinado') e a lista de séries (series) detalhando repetições (reps) e peso em kg (weight) individualmente para cada série.";
            return callAudioPrompt("workout", audioPrompt, audioBytes, WorkoutDto.class);
        }
        String textPrompt = """
                Analise o treino informado: "{text}".
                Extraia a descrição do treino (description como o grupamento muscular, ex: 'Peito', 'Costas', 'Pernas'), a duração em minutos (durationMinutes) e a lista de exercícios (exercises).
                Cada exercício deve conter o nome do exercício (name, ex: 'Supino Inclinado') e a lista de séries (series) detalhando repetições (reps) e peso em kg (weight) individualmente para cada série. Nunca agrupe séries com cargas ou repetições diferentes.
                """;
        return callTextPrompt("workout", textPrompt, text, WorkoutDto.class);
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
        return callTextPrompt("goal", "Solicitação do usuário para definir metas: \"{text}\".\n" + instruction, text, GoalDto.class);
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
        ByteArrayResource resource = new ByteArrayResource(audioBytes);
        Media media = new Media(MimeType.valueOf("audio/ogg"), resource);
        UserMessage userMessage = UserMessage.builder()
                .text(promptText)
                .media(media)
                .build();
        return executeWithMetrics(metricType, () -> chatClient.prompt(new Prompt(userMessage))
                .call()
                .entity(responseType));
    }

    private <T> T callTextPrompt(String metricType, String promptTemplate, String userText, Class<T> responseType) {
        return executeWithMetrics(metricType, () -> chatClient.prompt()
                .user(u -> u.text(promptTemplate).param("text", userText))
                .call()
                .entity(responseType));
    }

    private <T> T executeWithMetrics(String metricType, Supplier<T> supplier) {
        meterRegistry.counter("shapelog.gemini.calls", "type", metricType).increment();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = CompletableFuture.supplyAsync(supplier)
                    .get(GEMINI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sample.stop(meterRegistry.timer("shapelog.gemini.call.duration", "type", metricType));
            return result;
        } catch (Exception e) {
            meterRegistry.counter("shapelog.gemini.errors", "type", metricType).increment();
            throw new RuntimeException("Erro ao chamar API do Gemini (timeout ou falha): " + e.getMessage(), e);
        }
    }
}
