package com.bot.telegram.repository;

import com.bot.telegram.model.UserTelegram;
import com.bot.telegram.model.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {
    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);
    List<WorkoutSession> findByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(UserTelegram user, Instant start, Instant end);
    Optional<WorkoutSession> findFirstByUserAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(UserTelegram user, Instant start, Instant end);
}
