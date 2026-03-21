package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.AiPredictRequestDTO;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import com.example.hauiTrash.entity.AiRequest;
import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.repository.AiRequestRepository;
import com.example.hauiTrash.repository.DetectionRepository;
import com.example.hauiTrash.service.AiYoloService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiYoloServiceImpl implements AiYoloService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AiRequestRepository aiRequestRepo;

    @Autowired
    private DetectionRepository detectionRepo;

    @Value("${ai.yolo.base-url:http://127.0.0.1:8000}")
    private String yoloBaseUrl;

    private static final float LOW_CONF_THRESHOLD = 0.5f;

    @Override
    @Transactional
    public YoloPredictResponseDTO predictAndSave(AiPredictRequestDTO req) {

        if (req == null || req.getAiRequestId() == null) {
            throw new IllegalArgumentException("aiRequestId is required");
        }

        AiRequest aiRequest = aiRequestRepo.findById(req.getAiRequestId())
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + req.getAiRequestId()));

        Map<String, Object> body = new HashMap<>();
        body.put("request_id", req.getAiRequestId());
        body.put("image_url", req.getImageUrl());
        body.put("conf", req.getConf());
        body.put("iou", req.getIou());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = yoloBaseUrl + "/predict-image-url";

        ResponseEntity<YoloPredictResponseDTO> resp = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                YoloPredictResponseDTO.class
        );

        YoloPredictResponseDTO yolo = resp.getBody();
        if (yolo == null) {
            throw new RuntimeException("YOLO response is null");
        }

        detectionRepo.deleteByAiRequest_Id(req.getAiRequestId());

        List<Detection> rows = new ArrayList<>();

        if (yolo.getDetections() != null && !yolo.getDetections().isEmpty()) {
            for (YoloPredictResponseDTO.DetectionDTO d : yolo.getDetections()) {

                String labelNorm = normalizeLabel(d.getLabel());
                String labelDisplay = normalizeLabelDisplay(d.getLabelDisplay());

                if (labelDisplay == null) {
                    labelDisplay = labelNorm;
                }

                Float confidence = d.getConfidence();

                boolean needsConfirm = Boolean.TRUE.equals(d.getNeedsConfirm())
                        || (confidence != null && confidence < LOW_CONF_THRESHOLD);

                String status = needsConfirm ? "NEEDS_CONFIRM" : "DETECTED";

                rows.add(Detection.builder()
                        .aiRequest(aiRequest)
                        .label(labelNorm)
                        .labelDisplay(labelDisplay)
                        .confidence(confidence)
                        .annotatedUrl(d.getAnnotatedUrl() != null ? d.getAnnotatedUrl() : yolo.getAnnotatedUrl())
                        .status(status)
                        .x1(d.getX1())
                        .y1(d.getY1())
                        .x2(d.getX2())
                        .y2(d.getY2())
                        .cropUrl(d.getCropUrl())
                        .build());
            }

            detectionRepo.saveAll(rows);
        }

        return yolo;
    }

    private String normalizeLabel(String label) {
        if (label == null) return null;
        String s = label.trim();
        if (s.isEmpty()) return null;
        return s.toLowerCase(Locale.ROOT);
    }

    private String normalizeLabelDisplay(String labelDisplay) {
        if (labelDisplay == null) return null;
        String s = labelDisplay.trim();
        return s.isEmpty() ? null : s;
    }
}