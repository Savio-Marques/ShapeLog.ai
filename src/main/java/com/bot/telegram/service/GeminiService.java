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

import java.util.List;

@Service
public class GeminiService {

    private final ChatClient chatClient;

    public GeminiService(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    public MealDto parseMeal(String text, byte[] audioBytes) {
        if (audioBytes != null && audioBytes.length > 0) {
            ByteArrayResource resource = new ByteArrayResource(audioBytes);
            Media media = new Media(MimeType.valueOf("audio/ogg"), resource);
            UserMessage userMessage = UserMessage.builder()
                .text("Analise o áudio em anexo contendo o relato da refeição consumida. Extraia a descrição dos alimentos e estime as calorias, carboidratos (carbs), proteínas (protein) e gorduras (fat) em gramas. Retorne no formato estruturado.")
                .media(media)
                .build();
            return chatClient.prompt(new Prompt(userMessage))
                .call()
                .entity(MealDto.class);
        } else {
            return chatClient.prompt()
                .user(u -> u.text("""
                    Analise a refeição informada: "{text}".
                    Estime a quantidade total de calorias (cal), carboidratos (carbs), proteínas (protein) e gorduras (fat) em gramas de forma proporcional.
                    Exemplo: 100g de arroz branco possui aproximadamente 28g de carboidratos, 2g de proteína e 0g de gordura.
                    """)
                    .param("text", text))
                .call()
                .entity(MealDto.class);
        }
    }

    public WorkoutDto parseWorkout(String text, byte[] audioBytes) {
        if (audioBytes != null && audioBytes.length > 0) {
            ByteArrayResource resource = new ByteArrayResource(audioBytes);
            Media media = new Media(MimeType.valueOf("audio/ogg"), resource);
            UserMessage userMessage = UserMessage.builder()
                .text("Analise o áudio em anexo contendo o relato do treino realizado. Extraia a descrição, a duração estimada em minutos (durationMinutes) e a lista de exercícios contendo nome do exercício, número de séries (sets), repetições (reps) e peso utilizado em kg (weight) se forem citados.")
                .media(media)
                .build();
            return chatClient.prompt(new Prompt(userMessage))
                .call()
                .entity(WorkoutDto.class);
        } else {
            return chatClient.prompt()
                .user(u -> u.text("""
                    Analise o treino informado: "{text}".
                    Extraia a descrição geral do treino, a duração em minutos e a lista estruturada de exercícios.
                    Para cada exercício, identifique: nome do exercício, séries (sets), repetições (reps) e peso em kg (weight) se forem citados.
                    """)
                    .param("text", text))
                .call()
                .entity(WorkoutDto.class);
        }
    }
}
