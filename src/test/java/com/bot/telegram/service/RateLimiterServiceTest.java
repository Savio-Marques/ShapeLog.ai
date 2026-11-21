package com.bot.telegram.service;

import com.bot.telegram.exception.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private AtomicLong currentMillis;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        currentMillis = new AtomicLong(1_700_000_000_000L);
        Clock mutableClock = new Clock() {
            @Override
            public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override
            public Clock withZone(ZoneId zone) { return this; }
            @Override
            public Instant instant() { return Instant.ofEpochMilli(currentMillis.get()); }
            @Override
            public long millis() { return currentMillis.get(); }
        };
        rateLimiterService = new RateLimiterService(3, 5, meterRegistry, mutableClock);
    }

    @Test
    void checkAllowed_acimaDoLimitePorMinuto_deveBloquearEContabilizarMetrica() {
        rateLimiterService.checkAllowed(100L);
        rateLimiterService.checkAllowed(100L);
        rateLimiterService.checkAllowed(100L);

        assertThrows(RateLimitExceededException.class, () -> rateLimiterService.checkAllowed(100L));
        assertEquals(1.0, meterRegistry.counter("shapelog.ratelimit.rejected").count());

        // Outro usuário não deve ser afetado
        assertDoesNotThrow(() -> rateLimiterService.checkAllowed(200L));
    }

    @Test
    void checkAllowed_aposJanelaDeUmMinutoExpirar_deveLiberarAteLimitePorHora() {
        rateLimiterService.checkAllowed(100L);
        rateLimiterService.checkAllowed(100L);
        rateLimiterService.checkAllowed(100L);

        // Avança 61 segundos
        currentMillis.addAndGet(61_000L);
        rateLimiterService.checkAllowed(100L);
        rateLimiterService.checkAllowed(100L);

        // 6ª solicitação dentro da mesma hora deve bater no limite horário (5/h)
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> rateLimiterService.checkAllowed(100L));
        assertTrue(ex.getMessage().contains("5/hora"));

        // Avança 1 hora: janela limpa e libera novamente
        currentMillis.addAndGet(3_600_001L);
        assertDoesNotThrow(() -> rateLimiterService.checkAllowed(100L));
    }
}
