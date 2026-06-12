package com.bot.telegram.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "workout_sessions")
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

    private String description;

    private Integer durationMinutes;

    @Column(columnDefinition = "TEXT")
    private String exercisesJson;

    private LocalDateTime createdAt;

    private Integer userMessageId;

    private Integer botMessageId;
}
