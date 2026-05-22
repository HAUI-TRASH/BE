package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.StoryDetailDTO;
import com.example.hauiTrash.dto.StoryListDTO;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.entity.Story;
import com.example.hauiTrash.entity.StoryCategory;
import com.example.hauiTrash.repository.AccountRepository;
import com.example.hauiTrash.repository.StoryRepository;
import com.example.hauiTrash.service.PointService;
import com.example.hauiTrash.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final PointService pointService;
    @Override
    @Transactional(readOnly = true)
    public List<StoryListDTO> getPublishedStories(StoryCategory category, Pageable pageable) {

        // Nếu không có chọn category, dùng mặc định
        if (category == null ) {
            category = StoryCategory.TIPS;
        }
        List<Story> stories = storyRepository.findByCategory(category, pageable);
        return stories.stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StoryDetailDTO getStoryBySlug(String slug) {
        Story story = storyRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu chuyện: " + slug));

        // Tăng view count
        story.setViewCount(story.getViewCount() + 1);
        storyRepository.save(story);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Account account) {
            pointService.addPoints(account.getId(), "LEARN_STORY", slug, "Đọc câu chuyện: " + story.getTitle());
        }
        return toDetailDTO(story);
    }

    private StoryListDTO toListDTO(Story story) {
        return StoryListDTO.builder()
                .id(story.getId())
                .title(story.getTitle())
                .slug(story.getSlug())
                .thumbnailUrl(story.getThumbnailUrl())
                .category(story.getCategory().name())
                .viewCount(story.getViewCount())
                .publishedAt(story.getPublishedAt())
                .build();
    }

    private StoryDetailDTO toDetailDTO(Story story) {
        return StoryDetailDTO.builder()
                .id(story.getId())
                .title(story.getTitle())
                .slug(story.getSlug())
                .content(story.getContent())
                .thumbnailUrl(story.getThumbnailUrl())
                .category(story.getCategory().name())
                .viewCount(story.getViewCount())
                .publishedAt(story.getPublishedAt())
                .build();
    }
}