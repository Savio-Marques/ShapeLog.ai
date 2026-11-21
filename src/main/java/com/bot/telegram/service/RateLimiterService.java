package com.bot.telegram.service;

import com.bot.telegram.exception.RateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RateLimiterService {

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long ONE_HOUR_MS = 3_600_000L;

    private final int maxPerMinute;
    private final int maxPerHour;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterService(
            @Value("${shapelog.ratelimit.ai-per-minute:5}") int maxPerMinute,
            @Value("${shapelog.ratelimit.ai-per-hour:30}") int maxPerHour,
            MeterRegistry meterRegistry) {
        this(maxPerMinute, maxPerHour, meterRegistry, Clock.systemUTC());
    }

    RateLimiterService(int maxPerMinute, int maxPerHour, MeterRegistry meterRegistry, Clock clock) {
        this.maxPerMinute = maxPerMinute;
        this.maxPerHour = maxPerHour;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public void checkAllowed(Long chatId) {
        if (chatId == null) {
            return;
        }
        long now = clock.millis();
        AtomicReference<String> violationMessage = new AtomicReference<>();

        requestTimestamps.compute(chatId, (id, timestamps) -> {
            Deque<Long> deque = timestamps != null ? timestamps : new ArrayDeque<>();
            pruneOlderThanOneHour(deque, now);

            if (deque.size() >= maxPerHour) {
                violationMessage.set("⏳ Limite horário de consultas à IA atingido (" + maxPerHour + "/hora). Tente novamente mais tarde!");
                return deque;
            }

            long countLastMinute = 0;
            for (Long ts : deque) {
                if ((now - ts) < ONE_MINUTE_MS) {
                    countLastMinute++;
                }
            }
            if (countLastMinute >= maxPerMinute) {
                violationMessage.set("⏳ Muitas solicitações em sequência! Aguarde alguns segundos antes de enviar outra consulta à IA.");
                return deque;
            }

            deque.addLast(now);
            return deque;
        });

        String violation = violationMessage.get();
        if (violation != null) {
            if (meterRegistry != null) {
                meterRegistry.counter("shapelog.ratelimit.rejected").increment();
            }
            throw new RateLimitExceededException(violation);
        }
    }

    @Scheduled(fixedRate = 600000)
    public void cleanupExpiredEntries() {
        long now = clock.millis();
        requestTimestamps.forEach((chatId, deque) -> {
            requestTimestamps.computeIfPresent(chatId, (id, existing) -> {
                pruneOlderThanOneHour(existing, now);
                return existing.isEmpty() ? null : existing;
            });
        });
    }

    private void pruneOlderThanOneHour(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && (now - deque.peekFirst()) >= ONE_HOUR_MS) {
            deque.removeFirst();
        }
    }
}
