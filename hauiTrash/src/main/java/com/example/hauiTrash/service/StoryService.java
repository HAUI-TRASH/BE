package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.StoryDetailDTO;
import com.example.hauiTrash.dto.StoryListDTO;
import com.example.hauiTrash.entity.StoryCategory;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface StoryService {
    List<StoryListDTO> getPublishedStories(StoryCategory category, Pageable pageable);
    StoryDetailDTO getStoryBySlug(String slug);
}