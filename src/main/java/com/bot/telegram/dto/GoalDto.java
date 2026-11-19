package com.bot.telegram.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalDto {
    private Integer calories;
    private Integer protein;
    private Integer carbs;
    private Integer fat;
    private String explanation;
}
