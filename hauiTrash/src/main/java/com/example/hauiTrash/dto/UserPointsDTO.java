package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserPointsDTO {
    private Integer totalPoints;// bỏ level
}