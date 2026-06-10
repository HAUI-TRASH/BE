package com.example.hauiTrash.iot.controller;

import com.example.hauiTrash.iot.dto.*;
import com.example.hauiTrash.iot.service.IotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/iot")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class IotController {

    private final IotService iotService;

    /**
     * Tạo mới AI request từ ảnh upload.
     * Ảnh được upload lên Cloudinary, tạo bản ghi AiRequest.
     */
    @PostMapping(value = "/ai_request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IotAiRequestResponseDTO> createAiRequest(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(iotService.createAiRequest(file));
    }

    /**
     * Lấy chi tiết kết quả AI response theo aiRequestId.
     */
    @GetMapping("/ai_response/{id}/detail")
    public ResponseEntity<IotDetailResponseDTO> getDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(iotService.getDetail(id));
    }

    /**
     * Dự đoán (YOLO) và lưu kết quả detection.
     */
    @PostMapping("/predict")
    public ResponseEntity<IotPredictResponseDTO> predict(@RequestBody IotPredictRequestDTO req) {
        return ResponseEntity.ok(iotService.predict(req));
    }

    /**
     * Nhận diện rác realtime từ ảnh (REST endpoint).
     * Trả về kết quả detection tốt nhất (confidence cao nhất).
     */
    @PostMapping("/realtime")
    public ResponseEntity<IotRealtimeResponseDTO> detectRealtime(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(iotService.detectRealtime(file));
    }
}
