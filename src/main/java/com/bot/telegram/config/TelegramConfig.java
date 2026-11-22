package com.bot.telegram.config;

import com.bot.telegram.bot.FitnessBot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZoneId;
import java.util.List;

@Configuration
@EnableScheduling
public class TelegramConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramConfig.class);

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ZoneId appZoneId(@Value("${shapelog.timezone:America/Sao_Paulo}") String timezone) {
        return ZoneId.of(timezone);
    }

    @Bean(name = "geminiExecutor")
    public SimpleAsyncTaskExecutor geminiExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("gemini-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(16);
        executor.setTaskTerminationTimeout(15_000L);
        return executor;
    }

    @Bean(name = "botUpdateExecutor")
    public SimpleAsyncTaskExecutor botUpdateExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("bot-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(32);
        executor.setTaskTerminationTimeout(15_000L);
        return executor;
    }

    @Bean
    public TelegramClient telegramClient(@Value("${telegram.bot.token}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean(destroyMethod = "close")
    public TelegramBotsLongPollingApplication telegramBotsLongPollingApplication(
            FitnessBot fitnessBot,
            TelegramClient telegramClient,
            @Value("${telegram.bot.token}") String botToken) throws TelegramApiException {
        log.info("Registrando bot no Telegram (Long Polling)...");
        TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication();
        app.registerBot(botToken, fitnessBot);
        registerBotCommands(telegramClient);
        log.info("Bot registrado com sucesso.");
        return app;
    }

    /**
     * Publica o menu nativo de barra (/) no Telegram mantendo comandos administrativos ocultos.
     */
    private void registerBotCommands(TelegramClient telegramClient) {
        List<BotCommand> commands = List.of(
                new BotCommand("relatorio", "Ver progresso e registros do dia"),
                new BotCommand("refeicao", "Registrar uma refeição"),
                new BotCommand("treino", "Iniciar ou renomear treino do dia"),
                new BotCommand("exercicio", "Adicionar exercício ao treino"),
                new BotCommand("meta", "Ver ou atualizar metas diárias"),
                new BotCommand("start", "Guia rápido do ShapeLog.ai")
        );
        try {
            telegramClient.execute(SetMyCommands.builder().commands(commands).build());
            log.info("Menu de comandos nativo registrado ({} comandos).", commands.size());
        } catch (Exception e) {
            // Falha ao publicar menu não deve impedir a inicialização do polling
            log.warn("Falha ao registrar menu de comandos no Telegram: {}", e.getMessage());
        }
    }
}
