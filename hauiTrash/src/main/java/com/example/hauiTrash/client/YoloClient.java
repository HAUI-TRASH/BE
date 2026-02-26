package com.example.hauiTrash.client;

import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class YoloClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.yolo.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    public YoloPredictResponseDTO predictByImageUrl(Integer requestId, String imageUrl, float conf, float iou) {
        String url = baseUrl + "/predict-image-url";

        Map<String, Object> body = Map.of(
                "request_id", requestId,
                "image_url", imageUrl,
                "conf", conf,
                "iou", iou
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<YoloPredictResponseDTO> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                YoloPredictResponseDTO.class
        );

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new RuntimeException("YOLO predict failed");
        }
        return res.getBody();
    }
}