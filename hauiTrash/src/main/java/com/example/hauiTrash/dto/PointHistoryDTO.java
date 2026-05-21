package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class PointHistoryDTO {
    private Integer points;
    private String actionType;
    private String description;
    private Instant createdAt;
}