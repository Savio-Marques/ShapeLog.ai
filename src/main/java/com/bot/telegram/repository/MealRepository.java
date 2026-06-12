package com.bot.telegram.repository;

import com.bot.telegram.model.Meal;
import com.bot.telegram.model.UserTelegram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealRepository extends JpaRepository<Meal, Long> {
    List<Meal> findByUserAndCreatedAtBetween(UserTelegram user, LocalDateTime start, LocalDateTime end);
}
