package com.bot.telegram.repository;

import com.bot.telegram.model.UserStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface UserStateRepository extends JpaRepository<UserStateEntity, Long> {
    void deleteByExpiresAtBefore(Instant now);
}
