package com.bot.telegram.bot;

import com.bot.telegram.model.PendingInputEntity;
import com.bot.telegram.model.UserStateEntity;
import com.bot.telegram.repository.PendingInputRepository;
import com.bot.telegram.repository.UserStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);
    private static final long STATE_TTL_MS = 15 * 60 * 1000L;

    private final Map<Long, String> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Long> userStateTimes = new ConcurrentHashMap<>();
    private final Map<Long, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    private final UserStateRepository userStateRepository;
    private final PendingInputRepository pendingInputRepository;

    public StateManager() {
        this(null, null);
    }

    @Autowired
    public StateManager(UserStateRepository userStateRepository, PendingInputRepository pendingInputRepository) {
        this.userStateRepository = userStateRepository;
        this.pendingInputRepository = pendingInputRepository;
    }

    @Transactional
    public void putState(long chatId, String state) {
        if (userStateRepository != null) {
            Instant expiresAt = Instant.now().plusMillis(STATE_TTL_MS);
            userStateRepository.save(UserStateEntity.builder()
                    .chatId(chatId)
                    .state(state)
                    .expiresAt(expiresAt)
                    .build());
            return;
        }
        userStates.put(chatId, state);
        userStateTimes.put(chatId, System.currentTimeMillis());
    }

    @Transactional
    public String getState(long chatId) {
        if (userStateRepository != null) {
            Optional<UserStateEntity> opt = userStateRepository.findById(chatId);
            if (opt.isEmpty()) {
                return null;
            }
            UserStateEntity entity = opt.get();
            if (Instant.now().isAfter(entity.getExpiresAt())) {
                userStateRepository.delete(entity);
                return null;
            }
            return entity.getState();
        }
        Long timestamp = userStateTimes.get(chatId);
        if (timestamp == null || System.currentTimeMillis() - timestamp > STATE_TTL_MS) {
            userStates.remove(chatId);
            userStateTimes.remove(chatId);
            return null;
        }
        return userStates.get(chatId);
    }

    @Transactional
    public void removeState(long chatId) {
        if (userStateRepository != null) {
            userStateRepository.findById(chatId).ifPresent(userStateRepository::delete);
            return;
        }
        userStates.remove(chatId);
        userStateTimes.remove(chatId);
    }

    @Transactional
    public String putPendingInput(long chatId, String text, byte[] audioBytes, Integer userMessageId) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        if (pendingInputRepository != null) {
            if (audioBytes != null && audioBytes.length > 0) {
                pendingInputRepository.findById(chatId).ifPresent(pendingInputRepository::delete);
                pendingInputs.put(chatId, new PendingInput(token, text, audioBytes, userMessageId, now.toEpochMilli()));
            } else {
                pendingInputs.remove(chatId);
                pendingInputRepository.save(PendingInputEntity.builder()
                        .chatId(chatId)
                        .token(token)
                        .textContent(text)
                        .userMessageId(userMessageId)
                        .createdAt(now)
                        .expiresAt(now.plusMillis(STATE_TTL_MS))
                        .build());
            }
            return token;
        }
        pendingInputs.put(chatId, new PendingInput(token, text, audioBytes, userMessageId, now.toEpochMilli()));
        return token;
    }

    @Transactional
    public PendingInput consumePendingInput(long chatId, String token) {
        PendingInput memoryResult = consumeInMemoryPendingInput(chatId, token);
        if (memoryResult != null) {
            return memoryResult;
        }
        if (pendingInputRepository == null) {
            return null;
        }
        Optional<PendingInputEntity> opt = pendingInputRepository.findById(chatId);
        if (opt.isEmpty()) {
            return null;
        }
        PendingInputEntity entity = opt.get();
        if (Instant.now().isAfter(entity.getExpiresAt())) {
            pendingInputRepository.delete(entity);
            return null;
        }
        if (token != null && token.equals(entity.getToken())) {
            pendingInputRepository.delete(entity);
            return new PendingInput(
                    entity.getToken(),
                    entity.getTextContent(),
                    null,
                    entity.getUserMessageId(),
                    entity.getCreatedAt().toEpochMilli()
            );
        }
        return null;
    }

    private PendingInput consumeInMemoryPendingInput(long chatId, String token) {
        AtomicReference<PendingInput> consumed = new AtomicReference<>();
        long now = System.currentTimeMillis();
        pendingInputs.compute(chatId, (id, entry) -> {
            if (entry == null) {
                return null;
            }
            if ((now - entry.createdAt()) > STATE_TTL_MS) {
                return null;
            }
            if (token != null && token.equals(entry.token())) {
                consumed.set(entry);
                return null;
            }
            return entry;
        });
        return consumed.get();
    }

    @Transactional
    public void removePendingInput(long chatId) {
        pendingInputs.remove(chatId);
        if (pendingInputRepository != null) {
            pendingInputRepository.findById(chatId).ifPresent(pendingInputRepository::delete);
        }
    }

    @Scheduled(fixedRate = 600000)
    @Transactional
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
        pendingInputs.entrySet().removeIf(entry -> {
            boolean expirou = (agora - entry.getValue().createdAt()) > STATE_TTL_MS;
            if (expirou) {
                log.debug("Entrada pendente expirada removida para chatId={}", entry.getKey());
            }
            return expirou;
        });
        Instant now = Instant.now();
        if (userStateRepository != null) {
            userStateRepository.deleteByExpiresAtBefore(now);
        }
        if (pendingInputRepository != null) {
            pendingInputRepository.deleteByExpiresAtBefore(now);
        }
    }
}
