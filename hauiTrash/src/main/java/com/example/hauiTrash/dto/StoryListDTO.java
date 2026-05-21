package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class StoryListDTO {
    private Long id;
    private String title;
    private String slug;
    private String thumbnailUrl;
    private String category;
    private Integer viewCount;
    private Instant publishedAt;
}