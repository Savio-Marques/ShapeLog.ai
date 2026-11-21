package com.bot.telegram;

import com.bot.telegram.bot.FitnessBot;
import com.bot.telegram.service.GeminiService;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.TelegramBotsApi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class TelegramApplicationTests {

    @MockitoBean
    private TelegramBotsApi telegramBotsApi;

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private FitnessBot fitnessBot;

    @Autowired
    private MealService mealService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private GeminiService geminiService;

    @Test
    void contextLoads() {
        assertNotNull(fitnessBot);
        assertNotNull(mealService);
        assertNotNull(workoutService);
        assertNotNull(geminiService);
    }
}
