package com.bot.telegram.config;

import com.bot.telegram.bot.FitnessBot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@EnableScheduling
public class TelegramConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramConfig.class);

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(name = "geminiExecutor")
    public ThreadPoolTaskExecutor geminiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("gemini-io-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }

    @Bean(name = "botUpdateExecutor")
    public ThreadPoolTaskExecutor botUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bot-update-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(FitnessBot fitnessBot) throws TelegramApiException {
        log.info("Registrando bot no Telegram...");
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(fitnessBot);
        log.info("Bot registrado com sucesso.");
        return botsApi;
    }
}
