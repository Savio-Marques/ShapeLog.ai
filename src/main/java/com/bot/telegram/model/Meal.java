package com.bot.telegram.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "meals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserTelegram user;

    @Column(columnDefinition = "TEXT")
    private String rawInput;

    private String description;

    private Integer calories;

    private Double protein;

    private Double carbs;

    private Double fat;

    private LocalDateTime createdAt;
}
