package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.client.YoloClient;
import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.repository.*;
import com.example.hauiTrash.service.AiPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiPipelineServiceImpl implements AiPipelineService {

    @Autowired private AiRequestRepository aiRequestRepo;
    @Autowired private DetectionRepository detectionRepo;
    @Autowired private TrashItemRepository trashItemRepo;
    @Autowired private TrashTypeRepository trashTypeRepo;
    @Autowired private TrashItemMappingRepository mappingRepo;
    @Autowired private TrashItemKnowledgeRepository knowledgeRepo;

    @Autowired private YoloClient yoloClient;
    @Autowired private LlmClient llmClient; // predict không dùng, enrich click mới dùng

    private final float DEFAULT_CONF = 0.25f;
    private final float DEFAULT_IOU  = 0.60f;

    @Override
    @Transactional
    public AiResponseDetailsDTO predictAndEnrich(Integer requestId) {
        AiRequest req = aiRequestRepo.findByIdWithDetections(requestId)
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + requestId));

        // 1) YOLO
        YoloPredictResponseDTO yolo = yoloClient.predictByImageUrl(
                req.getId(), req.getCloudinaryUrl(), DEFAULT_CONF, DEFAULT_IOU
        );

        // 2) replace detections (orphanRemoval=true)
        req.getDetections().clear();

        if (yolo.getDetections() != null) {
            for (var d : yolo.getDetections()) {
                Detection det = Detection.builder()
                        .label(normLabel(d.getLabel()))
                        .labelDisplay(normLabelDisplay(d.getLabelDisplay()))
                        .confidence(d.getConfidence())
                        .annotatedUrl(d.getAnnotatedUrl())
                        .build();

                det.setAiRequest(req);
                req.getDetections().add(det);
            }
        }

        req.setFinishedAt(Instant.now());
        aiRequestRepo.save(req);

        // 3) build response: KHÔNG gọi Gemini
        return buildResponse_NoLlm(req, yolo.getAnnotatedUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public AiResponseDetailsDTO getDetail(Integer requestId) {
        AiRequest req = aiRequestRepo.findByIdWithDetections(requestId)
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + requestId));

        String annotatedUrl = (req.getDetections()!=null && !req.getDetections().isEmpty())
                ? req.getDetections().get(0).getAnnotatedUrl() : null;

        return buildResponseReadOnly_NoLlm(req, annotatedUrl);
    }

    // =========================
    // Build response (NO LLM)
    // =========================
    @Transactional
    protected AiResponseDetailsDTO buildResponse_NoLlm(AiRequest req, String annotatedUrl) {
        List<Detection> dets = (req.getDetections() == null) ? List.of() : req.getDetections();

        int rawCount = dets.size();
        float rawAvg = (float) dets.stream()
                .map(Detection::getConfidence)
                .filter(Objects::nonNull)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        // group theo label
        Map<String, List<Detection>> grouped = dets.stream()
                .filter(d -> d.getLabel() != null)
                .collect(Collectors.groupingBy(Detection::getLabel, LinkedHashMap::new, Collectors.toList()));

        // labelDisplay: YOLO -> DB -> fallback (NO LLM)
        Map<String, String> labelDisplayMap = resolveLabelDisplay_NoLlm(grouped);

        // cache để tránh query lặp
        Map<String, TrashItem> itemCache = new HashMap<>();

        List<AiResponseDetailsDTO.DetectionDTO> out = new ArrayList<>();

        for (var entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<Detection> same = entry.getValue();

            int quantity = same.size();
            float groupAvg = (float) same.stream()
                    .map(Detection::getConfidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0.0);

            Detection first = same.get(0);
            String labelDisplay = labelDisplayMap.getOrDefault(label, fallbackLabelDisplay(label));

            // đảm bảo trash_item tồn tại (NO LLM)
            TrashItem item = itemCache.computeIfAbsent(label, l -> getOrCreateTrashItem_NoLlm(l, labelDisplay));

            // mapping/knowledge: chỉ lấy DB, không có thì null
            TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_NoLlm(first, item, mapping, knowledge);

            dto.setId(null);
            dto.setTrashItemId(item.getId());
            dto.setQuantity(quantity);
            dto.setConfidence(groupAvg);
            dto.setLabelDisplay(labelDisplay);

            out.add(dto);
        }

        return AiResponseDetailsDTO.builder()
                .id(req.getId())
                .cloudinaryUrl(req.getCloudinaryUrl())
                .createdAt(req.getCreatedAt())
                .finishedAt(req.getFinishedAt())
                .accountId(req.getAccount() != null ? req.getAccount().getId() : null)
                .annotatedUrl(annotatedUrl)
                .count(rawCount)
                .confidenceAvg(rawAvg)
                .detections(out)
                .build();
    }

    @Transactional(readOnly = true)
    protected AiResponseDetailsDTO buildResponseReadOnly_NoLlm(AiRequest req, String annotatedUrl) {
        List<Detection> dets = (req.getDetections() == null) ? List.of() : req.getDetections();

        int rawCount = dets.size();
        float rawAvg = (float) dets.stream()
                .map(Detection::getConfidence)
                .filter(Objects::nonNull)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        Map<String, List<Detection>> grouped = dets.stream()
                .filter(d -> d.getLabel() != null)
                .collect(Collectors.groupingBy(Detection::getLabel, LinkedHashMap::new, Collectors.toList()));

        Map<String, String> labelDisplayMap = resolveLabelDisplay_NoLlm(grouped);

        List<AiResponseDetailsDTO.DetectionDTO> out = new ArrayList<>();

        for (var entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<Detection> same = entry.getValue();

            int quantity = same.size();
            float groupAvg = (float) same.stream()
                    .map(Detection::getConfidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0.0);

            Detection first = same.get(0);
            String labelDisplay = labelDisplayMap.getOrDefault(label, fallbackLabelDisplay(label));

            TrashItem item = trashItemRepo.findByLabel(label).orElse(null);
            TrashItemMapping mapping = (item == null) ? null : mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = (item == null) ? null : knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_NoLlm(first, item, mapping, knowledge);

            dto.setId(null);
            dto.setQuantity(quantity);
            dto.setConfidence(groupAvg);
            dto.setLabelDisplay(labelDisplay);
            if (item != null) dto.setTrashItemId(item.getId());

            out.add(dto);
        }

        return AiResponseDetailsDTO.builder()
                .id(req.getId())
                .cloudinaryUrl(req.getCloudinaryUrl())
                .createdAt(req.getCreatedAt())
                .finishedAt(req.getFinishedAt())
                .accountId(req.getAccount() != null ? req.getAccount().getId() : null)
                .annotatedUrl(annotatedUrl)
                .count(rawCount)
                .confidenceAvg(rawAvg)
                .detections(out)
                .build();
    }

    // =========================
    // LabelDisplay resolve (NO LLM)
    // =========================
    private Map<String, String> resolveLabelDisplay_NoLlm(Map<String, List<Detection>> grouped) {
        Map<String, String> result = new HashMap<>();

        // 1) YOLO
        for (var e : grouped.entrySet()) {
            String label = e.getKey();
            Detection first = e.getValue().get(0);
            String yoloLd = first.getLabelDisplay();
            if (yoloLd != null && !yoloLd.isBlank()) result.put(label, yoloLd.trim());
        }

        // 2) DB
        List<String> needDb = grouped.keySet().stream()
                .filter(l -> !result.containsKey(l))
                .toList();

        if (!needDb.isEmpty()) {
            List<TrashItem> items = trashItemRepo.findAllByLabelIn(needDb);
            for (TrashItem it : items) {
                if (it.getLabel() != null && it.getLabelDisplay() != null && !it.getLabelDisplay().isBlank()) {
                    result.put(it.getLabel(), it.getLabelDisplay().trim());
                }
            }
        }

        // 3) fallback
        for (String label : grouped.keySet()) {
            result.putIfAbsent(label, fallbackLabelDisplay(label));
        }

        return result;
    }

    // =========================
    // getOrCreate trash_item (NO LLM)
    // =========================
    protected TrashItem getOrCreateTrashItem_NoLlm(String label, String labelDisplay) {
        return trashItemRepo.findByLabel(label).map(item -> {
            if ((item.getLabelDisplay() == null || item.getLabelDisplay().isBlank())
                    && labelDisplay != null && !labelDisplay.isBlank()) {
                item.setLabelDisplay(labelDisplay);
                return trashItemRepo.save(item);
            }
            return item;
        }).orElseGet(() ->
                trashItemRepo.save(TrashItem.builder()
                        .label(label)
                        .labelDisplay(labelDisplay)
                        .status("NEED_REVIEW")
                        .build())
        );
    }

    // =========================
    // toDTO (NO LLM; null nếu chưa có)
    // =========================
    private AiResponseDetailsDTO.DetectionDTO toDTO_NoLlm(
            Detection det,
            TrashItem item,
            TrashItemMapping mapping,
            TrashItemKnowledge kn
    ) {
        String trashType = (mapping != null && mapping.getTrashType() != null)
                ? mapping.getTrashType().getName()
                : null;

        return AiResponseDetailsDTO.DetectionDTO.builder()
                .id(det.getId())
                .label(det.getLabel())
                .labelDisplay(det.getLabelDisplay() != null ? det.getLabelDisplay() : det.getLabel())
                .confidence(det.getConfidence() != null ? det.getConfidence() : 0f)
                .annotatedUrl(det.getAnnotatedUrl())
                .trashItemId(item != null ? item.getId() : null)

                // mapping/knowledge: có thì fill, không thì null
                .trashType(trashType)
                .material(kn != null ? kn.getMaterial() : null)
                .note(kn != null ? kn.getNote() : null)
                .action(kn != null ? kn.getAction() : null)
                .detail(kn == null ? null : AiResponseDetailsDTO.DetailDTO.builder()
                        .impact(kn.getImpact())
                        .toxicity(kn.getToxicity())
                        .safeSteps(kn.getSafeSteps())
                        .build())
                .build();
    }

    // =========================
    // Helpers
    // =========================
    private String fallbackLabelDisplay(String label) {
        if (label == null) return "Không rõ";
        String s = label.replace('_', ' ').trim();
        if (s.isEmpty()) return "Không rõ";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String normLabel(String label) {
        if (label == null) return null;
        String s = label.trim();
        if (s.isEmpty()) return null;
        return s.toLowerCase(Locale.ROOT);
    }

    private String normLabelDisplay(String labelDisplay) {
        if (labelDisplay == null) return null;
        String s = labelDisplay.trim();
        return s.isEmpty() ? null : s; // giữ tiếng Việt, không ép lower-case
    }
}
