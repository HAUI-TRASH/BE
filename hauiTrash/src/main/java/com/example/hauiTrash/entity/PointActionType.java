package com.example.hauiTrash.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum PointActionType {

    DETECTION_PLASTIC(5, List.of("plastic", "bottle", "cup", "bag", "nhựa")),
    DETECTION_PAPER(5, List.of("paper", "cardboard", "box", "newspaper", "giấy")),
    DETECTION_GLASS(5, List.of("glass", "jar", "chai", "ly", "thủy tinh")),
    DETECTION_METAL(5, List.of("metal", "can", "aluminum", "tin", "kim loại")),

    DETECTION_MULTI(5, null);  // Bonus cho nhiều object

    private final int points;
    private final List<String> keywords;

    /**
     * Tìm PointActionType từ label của YOLO
     */
    public static PointActionType fromLabel(String label) {
        if (label == null) return null;

        String lowerLabel = label.toLowerCase();

        for (PointActionType type : values()) {
            if (type.keywords != null) {
                for (String keyword : type.keywords) {
                    if (lowerLabel.contains(keyword)) {
                        return type;
                    }
                }
            }
        }
        return null;
    }
}