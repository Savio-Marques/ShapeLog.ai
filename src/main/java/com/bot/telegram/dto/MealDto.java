package com.bot.telegram.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealDto {
    private String description;
    private Integer calories;
    private Double protein;
    private Double carbs;
    private Double fat;
}
