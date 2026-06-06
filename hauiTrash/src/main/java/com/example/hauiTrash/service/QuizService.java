package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.*;
import java.util.List;

public interface QuizService {

    // Lấy quiz theo storyId (không random - cho admin xem)
    QuizDTO getQuizByStoryId(Long storyId);

    // Bắt đầu làm bài (random câu hỏi và đáp án)
    QuizStartDTO startQuiz(Integer accountId, Long storyId);

    // Nộp bài và chấm điểm
    QuizResultDTO submitQuiz(Integer accountId, QuizSubmitDTO submitData);

    // Lấy danh sách bài đã hoàn thành của user
    List<CompletedQuizDTO> getCompletedQuizzes(Integer accountId);
}