package com.bot.telegram.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "workout_sessions", indexes = {@Index(name = "idx_workout_user_created", columnList = "user_id, createdAt")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserTelegram user;

    @Column(columnDefinition = "TEXT")
    private String rawInput;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer durationMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    private String exercisesJson;

    private Instant createdAt;

    private Integer userMessageId;
    private Integer botMessageId;

    @Version
    private Long version;
}
