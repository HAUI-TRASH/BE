package com.example.hauiTrash.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiResponseDetailsDTO {

    /* =========================
     * Thông tin ai_request
     * ========================= */
    private Integer id;              // ai_request.id
    private String cloudinaryUrl;     // ai_request.cloudinary_url
    private Instant createdAt;        // ai_request.created_at
    private Instant finishedAt;       // ai_request.finished_at
    private Integer accountId;        // ai_request.account_id

    /* =========================
     * Tổng hợp kết quả YOLO
     * ========================= */
    private String annotatedUrl;      // detection.annotatedUrl (ảnh đã vẽ box)
    private Integer count;            // số detections
    private Float confidenceAvg;      // trung bình confidence

    /* =========================
     * Danh sách detection (raw + enrich)
     * ========================= */
    private List<DetectionDTO> detections;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetectionDTO {

        /* -------- Raw từ YOLO / DB detections -------- */
        private Integer id;            // detections.id
        private String label;          // yolo label (plastic_bottle)
        private String labelDisplay;   // hiển thị (Chai nhựa)
        private Float confidence;      // confidence
        private String annotatedUrl;   // link ảnh đã vẽ box
        private Integer trashItemId;

        /* -------- Mapping (LLM / DB) -------- */
        private String trashType;      // Hữu cơ / Vô cơ / Tái chế / Không xác định

        /* -------- Knowledge (LLM / DB cache) -------- */
        private String material;       // chất liệu
        private String note;           // lưu ý xử lý
        private String action;         // hành động đề xuất

        private DetailDTO detail;      // phân tích chi tiết
        private Integer quantity; // số lượng detect cùng label
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailDTO {
        private String impact;         // tác động môi trường / kinh tế
        private String toxicity;       // mức độ độc hại
        private List<String> safeSteps; // quy trình xử lý an toàn
    }
}