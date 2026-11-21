package com.bot.telegram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pending_inputs", indexes = {@Index(name = "idx_pending_inputs_expires", columnList = "expiresAt")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingInputEntity {

    @Id
    private Long chatId;

    @Column(nullable = false, length = 32)
    private String token;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    private Integer userMessageId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
