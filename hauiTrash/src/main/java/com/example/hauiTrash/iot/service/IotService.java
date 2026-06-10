package com.example.hauiTrash.iot.service;

import com.example.hauiTrash.iot.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface IotService {

    /**
     * Tạo mới một AI request từ ảnh upload.
     * Ảnh được upload lên Cloudinary, sau đó tạo bản ghi AiRequest.
     */
    IotAiRequestResponseDTO createAiRequest(MultipartFile file);

    /**
     * Lấy chi tiết kết quả AI response theo aiRequestId.
     */
    IotDetailResponseDTO getDetail(Integer aiRequestId);

    /**
     * Dự đoán (YOLO) và lưu kết quả detection.
     */
    IotPredictResponseDTO predict(IotPredictRequestDTO req);

    /**
     * Nhận diện rác realtime từ ảnh (REST endpoint).
     * Trả về kết quả detection tốt nhất (confidence cao nhất).
     */
    IotRealtimeResponseDTO detectRealtime(MultipartFile file);
}
