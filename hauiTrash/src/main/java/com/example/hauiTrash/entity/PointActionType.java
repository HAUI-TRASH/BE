package com.example.hauiTrash.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PointActionType {
    DETECTION(10),           // Phát hiện 1 loại rác mới
    DETECTION_MULTI(5),      // Mỗi object trong 1 request (tối đa 30)
    SHARE_RESULT(15),        // Chia sẻ kết quả
    LEARN_STORY(20),         // Đọc 1 câu chuyện
    COMPLETE_CHALLENGE(50),  // Hoàn thành thử thách
    DAILY_LOGIN(5);          // Đăng nhập mỗi ngày

    private final int points;


}