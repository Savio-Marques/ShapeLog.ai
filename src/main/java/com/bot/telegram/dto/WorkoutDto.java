package com.bot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkoutDto {

    private String description;
    private Integer durationMinutes;
    private List<ExerciseDto> exercises;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExerciseDto {
        private String id;
        private String name;
        private List<SeriesDto> series;
        private List<String> details;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeriesDto {
        private Integer reps;
        private Double weight;
    }
}
