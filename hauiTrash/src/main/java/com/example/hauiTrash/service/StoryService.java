package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.StoryDetailDTO;
import com.example.hauiTrash.dto.StoryListDTO;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface StoryService {
    List<StoryListDTO> getPublishedStories(String category, Pageable pageable);
    StoryDetailDTO getStoryBySlug(String slug);
}