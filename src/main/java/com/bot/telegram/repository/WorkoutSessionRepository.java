package com.bot.telegram.repository;

import com.bot.telegram.model.WorkoutSession;
import com.bot.telegram.model.UserTelegram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {
    List<WorkoutSession> findByUserAndCreatedAtBetween(UserTelegram user, LocalDateTime start, LocalDateTime end);
    Optional<WorkoutSession> findFirstByUserAndCreatedAtBetweenOrderByCreatedAtDesc(UserTelegram user, LocalDateTime start, LocalDateTime end);
}
