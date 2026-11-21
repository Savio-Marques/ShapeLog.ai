package com.bot.telegram.service;

import com.bot.telegram.dto.MealDto;
import com.bot.telegram.dto.WorkoutDto;
import com.bot.telegram.exception.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void executeWithMetrics_deveExecutarNoExecutorDedicadoENaoNoForkJoinCommonPool() {
        ExecutorService customExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "gemini-custom-test"));
        try {
            GeminiService serviceWithCustomPool = new GeminiService(chatModel, new SimpleMeterRegistry(), customExecutor);
            AtomicReference<String> threadName = new AtomicReference<>();

            String json = """
                    {
                      "description": "banana (~100g)",
                      "calories": 90,
                      "protein": 1.1,
                      "carbs": 23.0,
                      "fat": 0.3
                    }
                    """;
            when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
                threadName.set(Thread.currentThread().getName());
                return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
            });

            MealDto result = serviceWithCustomPool.parseMeal("1 banana", null);
            assertNotNull(result);
            assertEquals("gemini-custom-test", threadName.get());
        } finally {
            customExecutor.shutdownNow();
        }
    }

    @Test
    void executeWithMetrics_devePropagarMdcEAplicarRateLimitPorChatId() {
        RateLimiterService rateLimiter = new RateLimiterService(1, 10, new SimpleMeterRegistry());
        ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            GeminiService service = new GeminiService(chatModel, new SimpleMeterRegistry(), vtExecutor, rateLimiter);
            AtomicReference<String> mdcInsideWorker = new AtomicReference<>();
            String json = """
                    {
                      "description": "maçã (~130g)",
                      "calories": 70,
                      "protein": 0.3,
                      "carbs": 18.0,
                      "fat": 0.2
                    }
                    """;
            when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
                mdcInsideWorker.set(MDC.get("chatId"));
                return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
            });

            MDC.put("chatId", "12345");
            MealDto first = service.parseMeal("1 maçã", null);
            assertNotNull(first);
            assertEquals("12345", mdcInsideWorker.get());

            assertThrows(RateLimitExceededException.class, () -> service.parseMeal("outra maçã", null));
        } finally {
            MDC.clear();
            vtExecutor.shutdownNow();
        }
    }
}
