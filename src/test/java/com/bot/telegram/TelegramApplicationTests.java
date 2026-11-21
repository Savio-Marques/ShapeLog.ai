package com.bot.telegram;

import com.bot.telegram.bot.FitnessBot;
import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.repository.PendingInputRepository;
import com.bot.telegram.repository.UserStateRepository;
import com.bot.telegram.repository.UserTelegramRepository;
import com.bot.telegram.repository.WorkoutSessionRepository;
import com.bot.telegram.service.GeminiService;
import com.bot.telegram.service.MealService;
import com.bot.telegram.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TelegramApplicationTests {

    @MockitoBean
    private TelegramBotsLongPollingApplication telegramBotsLongPollingApplication;

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

    @Autowired
    @Qualifier("geminiExecutor")
    private SimpleAsyncTaskExecutor geminiExecutor;

    @Autowired
    @Qualifier("botUpdateExecutor")
    private SimpleAsyncTaskExecutor botUpdateExecutor;

    @Autowired
    private UserTelegramRepository userRepository;

    @Autowired
    private WorkoutSessionRepository workoutRepository;

    @Autowired
    private UserStateRepository userStateRepository;

    @Autowired
    private PendingInputRepository pendingInputRepository;

    @Test
    void contextLoads_virtualThreadsEJpaPersistenciaFuncionando() throws Exception {
        assertNotNull(fitnessBot);
        assertNotNull(mealService);
        assertNotNull(workoutService);
        assertNotNull(geminiService);
        assertNotNull(userStateRepository);
        assertNotNull(pendingInputRepository);

        Boolean geminiIsVirtual = CompletableFuture.supplyAsync(() -> Thread.currentThread().isVirtual(), geminiExecutor)
                .get(5, TimeUnit.SECONDS);
        Boolean botIsVirtual = CompletableFuture.supplyAsync(() -> Thread.currentThread().isVirtual(), botUpdateExecutor)
                .get(5, TimeUnit.SECONDS);
        assertTrue(geminiIsVirtual);
        assertTrue(botIsVirtual);

        UserTelegram user = userRepository.save(UserTelegram.builder()
                .id(777L)
                .firstName("Teste")
                .approved(true)
                .registeredAt(Instant.now())
                .build());

        WorkoutSession saved = workoutRepository.save(WorkoutSession.builder()
                .user(user)
                .rawInput("Treino Teste")
                .exercisesJson("[{\"id\":\"ex1\",\"name\":\"Supino\",\"series\":[{\"reps\":10,\"weight\":60.0}]}]")
                .createdAt(Instant.now())
                .build());

        WorkoutSession reloaded = workoutRepository.findByIdAndUserId(saved.getId(), 777L).orElseThrow();
        assertTrue(reloaded.getExercisesJson().contains("Supino"));
        assertNotNull(reloaded.getVersion());
    }
}
