package com.bot.telegram.repository;
import com.bot.telegram.model.UserTelegram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTelegramRepository extends JpaRepository<UserTelegram, Long> {
}
