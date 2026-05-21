package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserPointsDTO {
    private Integer totalPoints;
    private Integer level;
    private Integer pointsToNextLevel;
    private Integer nextLevelThreshold;
}