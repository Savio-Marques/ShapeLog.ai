package com.bot.telegram.repository;

import com.bot.telegram.model.PendingInputEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface PendingInputRepository extends JpaRepository<PendingInputEntity, Long> {
    void deleteByExpiresAtBefore(Instant now);
}
