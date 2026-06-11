package com.bot.telegram.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users_telegram")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTelegram {

    @Id
    private Long id; // Chat ID do Telegram

    private String username;
    
    private String firstName;

    private Integer targetCalories;

    private Integer targetProtein;

    private Integer targetCarbs;

    private Integer targetFat;

    private LocalDateTime registeredAt;
}
