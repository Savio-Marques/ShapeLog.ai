package com.bot.telegram.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutDto {
    private String description;
    private Integer durationMinutes;
    private List<ExerciseDto> exercises;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExerciseDto {
        private String name;
        private Integer sets;
        private String reps;
        private Double weight;
    }
}
