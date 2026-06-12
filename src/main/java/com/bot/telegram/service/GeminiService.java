package com.bot.telegram.service;

import com.bot.telegram.dto.MealDto;
import com.bot.telegram.dto.WorkoutDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GeminiService {

    private final ChatClient chatClient;

    public GeminiService(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    public MealDto parseMeal(String text, byte[] audioBytes) {
        // Item 4: validar se há algum conteúdo antes de chamar a API
        if ((text == null || text.isBlank()) && (audioBytes == null || audioBytes.length == 0)) {
            throw new IllegalArgumentException("Nenhum conteúdo para analisar. Envie um texto ou áudio válido.");
        }
        if (audioBytes != null && audioBytes.length > 0) {
            ByteArrayResource resource = new ByteArrayResource(audioBytes);
            Media media = new Media(MimeType.valueOf("audio/ogg"), resource);
            UserMessage userMessage = UserMessage.builder()
                .text("Analise o áudio em anexo contendo o relato da refeição consumida. " +
                      "Extraia os alimentos e estime os macronutrientes. " +
                      "Retorne um JSON com exatamente estes campos: " +
                      "\"description\" (liste cada alimento com sua quantidade e unidade em letras minúsculas separados por vírgula, ex: '1 pão francês, 4 ovos mexidos, 1 fatia de queijo coalho'), " +
                      "\"calories\" (total de calorias como número inteiro), " +
                      "\"protein\" (proteínas em gramas como decimal), " +
                      "\"carbs\" (carboidratos em gramas como decimal), " +
                      "\"fat\" (gorduras em gramas como decimal).")
                .media(media)
                .build();
            try {
                return CompletableFuture.supplyAsync(() -> chatClient.prompt(new Prompt(userMessage))
                    .call()
                    .entity(MealDto.class))
                    .get(45, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao chamar API do Gemini (timeout ou falha): " + e.getMessage(), e);
            }
        } else {
            try {
                return CompletableFuture.supplyAsync(() -> chatClient.prompt()
                    .user(u -> u.text("""
                        Analise a refeição informada: "{text}".
                        Estime os macronutrientes e retorne um JSON com exatamente estes campos:
                        - description: liste cada alimento com sua quantidade e unidade em letras minúsculas separados por vírgula (ex: '1 pão francês, 4 ovos mexidos, 1 fatia de queijo coalho')
                        - calories: total de calorias como número inteiro
                        - protein: proteínas em gramas como decimal
                        - carbs: carboidratos em gramas como decimal
                        - fat: gorduras em gramas como decimal
                        """)
                        .param("text", text))
                    .call()
                    .entity(MealDto.class))
                    .get(45, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao chamar API do Gemini (timeout ou falha): " + e.getMessage(), e);
            }
        }
    }

    public WorkoutDto parseWorkout(String text, byte[] audioBytes) {
        // Item 4: validar se há algum conteúdo antes de chamar a API
        if ((text == null || text.isBlank()) && (audioBytes == null || audioBytes.length == 0)) {
            throw new IllegalArgumentException("Nenhum conteúdo para analisar. Envie um texto ou áudio válido.");
        }
        if (audioBytes != null && audioBytes.length > 0) {
            ByteArrayResource resource = new ByteArrayResource(audioBytes);
            Media media = new Media(MimeType.valueOf("audio/ogg"), resource);
            UserMessage userMessage = UserMessage.builder()
                .text("Analise o áudio em anexo contendo o relato do treino realizado. Extraia a descrição do treino (description como o grupamento muscular, ex: 'Peito', 'Costas', 'Pernas'), a duração estimada em minutos (durationMinutes) e a lista de exercícios (exercises). Cada exercício deve conter o nome do exercício (name, ex: 'Supino Inclinado') e a lista de séries (series) detalhando repetições (reps) e peso em kg (weight) individualmente para cada série.")
                .media(media)
                .build();
            try {
                return CompletableFuture.supplyAsync(() -> chatClient.prompt(new Prompt(userMessage))
                    .call()
                    .entity(WorkoutDto.class))
                    .get(45, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao chamar API do Gemini (timeout ou falha): " + e.getMessage(), e);
            }
        } else {
            try {
                return CompletableFuture.supplyAsync(() -> chatClient.prompt()
                    .user(u -> u.text("""
                        Analise o treino informado: "{text}".
                        Extraia a descrição do treino (description como o grupamento muscular, ex: 'Peito', 'Costas', 'Pernas'), a duração em minutos (durationMinutes) e a lista de exercícios (exercises).
                        Cada exercício deve conter o nome do exercício (name, ex: 'Supino Inclinado') e a lista de séries (series) detalhando repetições (reps) e peso em kg (weight) individualmente para cada série. Nunca agrupe séries com cargas ou repetições diferentes.
                        """)
                        .param("text", text))
                    .call()
                    .entity(WorkoutDto.class))
                    .get(45, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao chamar API do Gemini (timeout ou falha): " + e.getMessage(), e);
            }
        }
    }
}
