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
        private List<SeriesDto> series;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeriesDto {
        private Integer reps;
        private Double weight;
    }
}
