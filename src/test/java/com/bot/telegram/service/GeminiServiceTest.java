package com.bot.telegram.service;

import com.bot.telegram.dto.MealDto;
import com.bot.telegram.dto.WorkoutDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiServiceTest {

    private ChatModel chatModel;
    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        geminiService = new GeminiService(chatModel, new SimpleMeterRegistry());
    }

    @Test
    void parseMeal_comAudioERespostaDivididaEmMultiplosParts_deveConcatenarSemErro() {
        Generation part1 = new Generation(new AssistantMessage("{\"description\": \"pão francês (~50g), queijo mu"));
        Generation part2 = new Generation(new AssistantMessage("ssarela (~30g)\", \"calories\": 320, \"protein\": 14.0, \"carbs\": 30.0, \"fat\": 15.0}"));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(part1, part2)));

        byte[] audio = new byte[]{1, 2, 3, 4};
        MealDto result = geminiService.parseMeal(null, audio);

        assertNotNull(result);
        assertEquals("pão francês (~50g), queijo mussarela (~30g)", result.getDescription());
        assertEquals(320, result.getCalories());
        assertEquals(14.0, result.getProtein());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt sentPrompt = captor.getValue();
        assertInstanceOf(GoogleGenAiChatOptions.class, sentPrompt.getOptions());
        GoogleGenAiChatOptions opts = (GoogleGenAiChatOptions) sentPrompt.getOptions();
        assertEquals("application/json", opts.getResponseMimeType());
        assertEquals(2048, opts.getMaxOutputTokens());
        UserMessage userMsg = sentPrompt.getUserMessage();
        assertEquals(1, userMsg.getMedia().size());
    }

    @Test
    void parseWorkout_cardioSemSeries_deveRetornarDetailsPreenchidos() {
        String json = """
                {
                  "description": "Cardio",
                  "durationMinutes": 30,
                  "exercises": [
                    {
                      "name": "Esteira",
                      "series": [],
                      "details": ["Duração: 30 min", "Velocidade: 10 km/h"]
                    }
                  ]
                }
                """;
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        WorkoutDto dto = geminiService.parseWorkout("Esteira 30 min velocidade 10 km/h", null);
        assertNotNull(dto);
        assertEquals("Cardio", dto.getDescription());
        assertEquals(30, dto.getDurationMinutes());
        assertEquals(1, dto.getExercises().size());
        assertEquals("Esteira", dto.getExercises().get(0).getName());
        assertEquals(List.of("Duração: 30 min", "Velocidade: 10 km/h"), dto.getExercises().get(0).getDetails());
    }
}
