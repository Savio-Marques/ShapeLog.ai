package com.bot.telegram.config;

import com.bot.telegram.bot.FitnessBot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class TelegramConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramConfig.class);

    /**
     * M2: ObjectMapper como bean Spring compartilhado — thread-safe após configurado,
     * evita instanciar um por classe (WorkoutService, MessageFormatter, ReportService).
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(FitnessBot fitnessBot) throws TelegramApiException {
        // M1: exceção propagada — se o registro falhar (token inválido, rede), a aplicação falha ao subir com log claro
        log.info("Registrando bot no Telegram...");
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(fitnessBot);
        log.info("Bot registrado com sucesso.");
        return botsApi;
    }
}