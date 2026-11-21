package com.bot.telegram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_states", indexes = {@Index(name = "idx_user_states_expires", columnList = "expiresAt")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStateEntity {

    @Id
    private Long chatId;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private Instant expiresAt;
}
