package com.bot.telegram.repository;

import com.bot.telegram.model.PendingInputEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PendingInputRepository extends JpaRepository<PendingInputEntity, String> {
    Optional<PendingInputEntity> findByTokenAndChatId(String token, Long chatId);
    void deleteByExpiresAtBefore(Instant now);
}
