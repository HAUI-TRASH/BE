package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.SendRequestResponse;
import com.example.hauiTrash.service.SendRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/send-request")
@CrossOrigin(origins = "*")
public class SendRequestController {

    @Autowired
    private SendRequestService sendRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<SendRequestResponse>> sendRequest(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "cloudinary_url", required = false) String cloudinaryUrl,
            @RequestParam(value = "account_id", required = false) String accountId
    ) throws IOException {

        if ((file == null || file.isEmpty()) && (cloudinaryUrl == null || cloudinaryUrl.isBlank())) {
            return ResponseEntity.badRequest().body(ApiResponse.<SendRequestResponse>builder()
                    .message("Vui lòng cung cấp file hoặc cloudinary_url")
                    .data(null)
                    .build());
        }

        SendRequestResponse resp = sendRequestService.createRequest(file, cloudinaryUrl, accountId);
        return ResponseEntity.ok(ApiResponse.<SendRequestResponse>builder()
                .message("Yêu cầu gửi ảnh đã được tạo")
                .data(resp)
                .build());
    }
}
