package com.bot.telegram.bot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);
    private static final long STATE_TTL_MS = 15 * 60 * 1000L;
    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> userStateTimes = new ConcurrentHashMap<>();

    public void putState(long chatId, String state) {
        userStates.put(chatId, state);
        userStateTimes.put(chatId, System.currentTimeMillis());
    }

    public String getState(long chatId) {
        Long timestamp = userStateTimes.get(chatId);
        if (timestamp == null || System.currentTimeMillis() - timestamp > STATE_TTL_MS) {
            userStates.remove(chatId);
            userStateTimes.remove(chatId);
            return null;
        }
        return userStates.get(chatId);
    }

    public void removeState(long chatId) {
        userStates.remove(chatId);
        userStateTimes.remove(chatId);
    }

    @Scheduled(fixedRate = 600000)
    public void limparEstadosExpirados() {
        long agora = System.currentTimeMillis();
        userStateTimes.entrySet().removeIf(entry -> {
            boolean expirou = (agora - entry.getValue()) > STATE_TTL_MS;
            if (expirou) {
                userStates.remove(entry.getKey());
                log.debug("Estado expirado removido para chatId={}", entry.getKey());
            }
            return expirou;
        });
    }
}
