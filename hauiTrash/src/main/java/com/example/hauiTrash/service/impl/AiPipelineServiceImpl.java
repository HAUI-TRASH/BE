package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.client.YoloClient;
import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.VisualRagResult;
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
    @Autowired private ClassificationRepository classificationRepo;

    @Autowired private DetectionFeedbackRepository feedbackRepo;
    @Autowired private ReviewQueueRepository reviewQueueRepo;
    @Autowired private TrashItemAliasRepository trashItemAliasRepo;

    @Autowired private YoloClient yoloClient;
    @Autowired private LlmClient llmClient;

    private final float DEFAULT_CONF = 0.25f;
    private final float DEFAULT_IOU  = 0.60f;
    private final float LOW_CONF_THRESHOLD = 0.50f;

    @Override
    @Transactional
    public AiResponseDetailsDTO predictAndEnrich(Integer requestId) {
        AiRequest req = aiRequestRepo.findByIdWithDetections(requestId)
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + requestId));

        // 1) YOLO predict
        YoloPredictResponseDTO yolo = yoloClient.predictByImageUrl(
                req.getId(),
                req.getCloudinaryUrl(),
                DEFAULT_CONF,
                DEFAULT_IOU
        );

        // 2) clear old detections
        req.getDetections().clear();

        if (yolo.getDetections() != null) {
            for (var d : yolo.getDetections()) {
                Float conf = d.getConfidence();

                Detection det = Detection.builder()
                        .label(normLabel(d.getLabel()))
                        .labelDisplay(normLabelDisplay(d.getLabelDisplay()))
                        .confidence(conf)
                        .annotatedUrl(d.getAnnotatedUrl())
                        .status((conf == null || conf < LOW_CONF_THRESHOLD) ? "NEEDS_CONFIRM" : "DETECTED")
                        .x1(d.getX1())
                        .y1(d.getY1())
                        .x2(d.getX2())
                        .y2(d.getY2())
                        .cropUrl(d.getCropUrl())
                        .build();

                det.setAiRequest(req);
                req.getDetections().add(det);
            }
        }

        req.setFinishedAt(Instant.now());
        aiRequestRepo.saveAndFlush(req);
        // 3) Visual RAG + Feedback Loop
        enrichDetectionsWithVisualRagAndFeedback(req);

        // 4) response
        return buildResponse_NoLlm(req, yolo.getAnnotatedUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public AiResponseDetailsDTO getDetail(Integer requestId) {
        AiRequest req = aiRequestRepo.findByIdWithDetections(requestId)
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + requestId));

        String annotatedUrl = (req.getDetections() != null && !req.getDetections().isEmpty())
                ? req.getDetections().get(0).getAnnotatedUrl()
                : null;

        return buildResponseReadOnly_NoLlm(req, annotatedUrl);
    }

    @Transactional
    protected void enrichDetectionsWithVisualRagAndFeedback(AiRequest req) {
        if (req.getDetections() == null || req.getDetections().isEmpty()) {
            return;
        }

        for (Detection det : req.getDetections()) {
            String rawLabel = det.getLabel();

            // 1) feedback override
            String finalLabel = applyFeedbackOverride(rawLabel);

            if (finalLabel != null && !finalLabel.equals(rawLabel)) {
                det.setLabel(finalLabel);
                if (det.getLabelDisplay() == null || det.getLabelDisplay().isBlank()) {
                    det.setLabelDisplay(fallbackLabelDisplay(finalLabel));
                }
            }

            // 2) đảm bảo trash_item tồn tại
            TrashItem item = getOrCreateTrashItem_NoLlm(det.getLabel(), det.getLabelDisplay());

            // 3) Visual RAG retrieval
            VisualRagResult rag = retrieveVisualRag(det.getLabel());

            // 4) classification
            if (rag != null) {
                Classification cls = Classification.builder()
                        .detection(det)
                        .trashItem(rag.getTrashItem())
                        .trashType(rag.getTrashType())
                        .source("RAG")
                        .confidence(rag.getMappingConfidence())
                        .note("Auto classified by Visual RAG")
                        .build();

                classificationRepo.save(cls);

                if (rag.getTrashItem() != null && item != null && !Objects.equals(item.getId(), rag.getTrashItem().getId())) {
                    det.setLabel(rag.getTrashItem().getLabel());
                    det.setLabelDisplay(
                            rag.getTrashItem().getLabelDisplay() != null
                                    ? rag.getTrashItem().getLabelDisplay()
                                    : fallbackLabelDisplay(rag.getTrashItem().getLabel())
                    );
                }
            }

            // 5) review queue nếu cần
            enqueueReviewIfNeeded(det, rag != null);
        }

        aiRequestRepo.saveAndFlush(req);
    }

    protected String applyFeedbackOverride(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return rawLabel;
        }

        return feedbackRepo.findTopByOriginalLabelOrderByIdDesc(rawLabel)
                .map(fb -> {
                    if (fb.getConfirmedLabel() != null && !fb.getConfirmedLabel().isBlank()) {
                        return normLabel(fb.getConfirmedLabel());
                    }
                    return rawLabel;
                })
                .orElse(rawLabel);
    }

    @Transactional(readOnly = true)
    protected VisualRagResult retrieveVisualRag(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }

        String normalized = normLabel(label);

        TrashItem item = trashItemAliasRepo.findByAlias(normalized)
                .map(TrashItemAlias::getTrashItem)
                .orElseGet(() -> trashItemRepo.findByLabel(normalized).orElse(null));

        if (item == null) {
            return null;
        }

        TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        TrashItemKnowledge knowledge = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

        return VisualRagResult.builder()
                .trashItem(item)
                .trashType(mapping != null ? mapping.getTrashType() : null)
                .knowledge(knowledge)
                .mappingConfidence(mapping != null ? mapping.getMappingConfidence() : null)
                .build();
    }

    protected void enqueueReviewIfNeeded(Detection det, boolean ragFound) {
        Float conf = det.getConfidence();
        String reason = null;
        String reasonCode = null;
        int priority = 1;

        if (conf == null || conf < LOW_CONF_THRESHOLD) {
            reason = "Độ tin cậy thấp, cần xác nhận.";
            reasonCode = "LOW_CONFIDENCE";
            priority = 10;
            det.setStatus("NEEDS_CONFIRM");
        } else if (!ragFound) {
            reason = "Không tìm thấy tri thức phù hợp.";
            reasonCode = "NO_RAG_MATCH";
            priority = 8;
        }

        if (reason == null) {
            return;
        }

        // QUAN TRỌNG: đảm bảo detection đã có ID
        if (det.getId() == null) {
            detectionRepo.saveAndFlush(det);
        }

        Integer detId = det.getId();
        if (detId == null) {
            throw new RuntimeException("Detection ID is null, cannot create review_queue");
        }

        boolean existsPending = reviewQueueRepo
                .findByRefTableAndRefIdAndQueueStatus("detections", detId, "PENDING")
                .isPresent();

        if (!existsPending) {
            ReviewQueue rq = ReviewQueue.builder()
                    .queueType("AI_REVIEW")
                    .refTable("detections")
                    .refId(detId)

                    .aiRequestId(det.getAiRequest() != null ? det.getAiRequest().getId() : null)
                    .detectionId(detId)
                    .trashItemId(null)

                    .entityType("DETECTION")
                    .entityId(detId)

                    .suggestedLabel(det.getLabel())
                    .suggestedTrashTypeId(null)
                    .suggestedConfidence(det.getConfidence())

                    .reasonCode(reasonCode)
                    .reason(reason)

                    .queueStatus("PENDING")
                    .status("PENDING")
                    .priority(priority)

                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            reviewQueueRepo.save(rq);
        }
    }

    @Transactional
    public void submitFeedback(Integer detectionId, String confirmedLabel, String feedbackType, String comment) {
        Detection det = detectionRepo.findById(detectionId)
                .orElseThrow(() -> new RuntimeException("Detection not found: " + detectionId));

        String normalizedConfirmed = normLabel(confirmedLabel);

        DetectionFeedback fb = DetectionFeedback.builder()
                .detection(det)
                .originalLabel(det.getLabel())
                .confirmedLabel(normalizedConfirmed)
                .feedbackType(feedbackType)
                .comment(comment)
                .createdAt(Instant.now())
                .build();

        feedbackRepo.save(fb);

        if ("REJECTED".equalsIgnoreCase(feedbackType)) {
            det.setStatus("REJECTED");
        } else {
            det.setStatus("CONFIRMED");

            if (normalizedConfirmed != null) {
                det.setLabel(normalizedConfirmed);
                det.setLabelDisplay(fallbackLabelDisplay(normalizedConfirmed));
            }

            TrashItem item = getOrCreateTrashItem_NoLlm(normalizedConfirmed, fallbackLabelDisplay(normalizedConfirmed));
            VisualRagResult rag = retrieveVisualRag(normalizedConfirmed);

            Classification cls = Classification.builder()
                    .detection(det)
                    .trashItem(rag != null && rag.getTrashItem() != null ? rag.getTrashItem() : item)
                    .trashType(rag != null ? rag.getTrashType() : null)
                    .source("FEEDBACK_RAG")
                    .confidence(1.0f)
                    .note("Re-classified from user feedback")
                    .build();

            classificationRepo.save(cls);
        }

        reviewQueueRepo.findByEntityTypeAndEntityIdAndStatus("DETECTION", det.getId(), "PENDING")
                .ifPresent(rq -> {
                    rq.setStatus("DONE");
                    rq.setResolvedAt(Instant.now());
                    reviewQueueRepo.save(rq);
                });

        detectionRepo.save(det);
    }

    // =========================
    // Build response
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

        Map<String, List<Detection>> grouped = dets.stream()
                .filter(d -> d.getLabel() != null)
                .collect(Collectors.groupingBy(Detection::getLabel, LinkedHashMap::new, Collectors.toList()));

        Map<String, String> labelDisplayMap = resolveLabelDisplay_NoLlm(grouped);
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

//            Detection first = same.get(0);
            Detection first = same.stream()
                    .filter(d -> d.getCropUrl() != null && !d.getCropUrl().isBlank())
                    .findFirst()
                    .orElseGet(() -> same.stream()
                            .filter(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                            .findFirst()
                            .orElse(same.get(0)));
            String labelDisplay = labelDisplayMap.getOrDefault(label, fallbackLabelDisplay(label));
            TrashItem item = itemCache.computeIfAbsent(label, l -> getOrCreateTrashItem_NoLlm(l, labelDisplay));

            TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_NoLlm(first, item, mapping, knowledge);

            dto.setId(first.getId());
            dto.setTrashItemId(item.getId());
            dto.setQuantity(quantity);
            dto.setConfidence(groupAvg);
            dto.setLabelDisplay(labelDisplay);

            boolean groupNeedsConfirm = same.stream()
                    .anyMatch(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()));
            dto.setStatus(groupNeedsConfirm ? "NEEDS_CONFIRM" : first.getStatus());

            out.add(dto);
        }
        boolean requiresConfirmation = dets.stream()
                .anyMatch(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()));

        return AiResponseDetailsDTO.builder()
                .id(req.getId())
                .cloudinaryUrl(req.getCloudinaryUrl())
                .createdAt(req.getCreatedAt())
                .finishedAt(req.getFinishedAt())
                .accountId(req.getAccount() != null ? req.getAccount().getId() : null)
                .annotatedUrl(annotatedUrl)
                .count(rawCount)
                .confidenceAvg(rawAvg)
                .requiresConfirmation(requiresConfirmation)
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

        boolean requiresConfirmation = dets.stream()
                .anyMatch(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()));

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

//            Detection first = same.get(0);
            Detection first = same.stream()
                    .filter(d -> d.getCropUrl() != null && !d.getCropUrl().isBlank())
                    .findFirst()
                    .orElseGet(() -> same.stream()
                            .filter(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                            .findFirst()
                            .orElse(same.get(0)));
            String labelDisplay = labelDisplayMap.getOrDefault(label, fallbackLabelDisplay(label));

            TrashItem item = trashItemRepo.findByLabel(label).orElse(null);
            TrashItemMapping mapping = (item == null) ? null : mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = (item == null) ? null : knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_NoLlm(first, item, mapping, knowledge);

            // giữ id thật để FE submit feedback được
            dto.setId(first.getId());
            dto.setQuantity(quantity);
            dto.setConfidence(groupAvg);
            dto.setLabelDisplay(labelDisplay);

            boolean groupNeedsConfirm = same.stream()
                    .anyMatch(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()));
            dto.setStatus(groupNeedsConfirm ? "NEEDS_CONFIRM" : first.getStatus());

            if (item != null) {
                dto.setTrashItemId(item.getId());
            }

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
                .requiresConfirmation(requiresConfirmation)
                .detections(out)
                .build();
    }

    private Map<String, String> resolveLabelDisplay_NoLlm(Map<String, List<Detection>> grouped) {
        Map<String, String> result = new HashMap<>();

        // 1. from YOLO
        for (var e : grouped.entrySet()) {
            String label = e.getKey();
            Detection first = e.getValue().get(0);
            String yoloLd = first.getLabelDisplay();

            if (yoloLd != null && !yoloLd.isBlank()) {
                result.put(label, yoloLd.trim());
            }
        }

        // 2. from DB
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

        // 3. fallback
        for (String label : grouped.keySet()) {
            result.putIfAbsent(label, fallbackLabelDisplay(label));
        }

        return result;
    }

    protected TrashItem getOrCreateTrashItem_NoLlm(String label, String labelDisplay) {
        if (label == null || label.isBlank()) {
            throw new RuntimeException("Label cannot be null/blank");
        }

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
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build())
        );
    }

    private AiResponseDetailsDTO.DetectionDTO toDTO_NoLlm(
            Detection det,
            TrashItem item,
            TrashItemMapping mapping,
            TrashItemKnowledge kn
    ) {
        Classification latestCls = null;
        if (det.getId() != null) {
            latestCls = classificationRepo.findTopByDetectionIdOrderByIdDesc(det.getId()).orElse(null);
        }

        TrashItem finalItem = item;
        TrashType finalType = mapping != null ? mapping.getTrashType() : null;

        if (latestCls != null) {
            if (latestCls.getTrashItem() != null) {
                finalItem = latestCls.getTrashItem();
            }
            if (latestCls.getTrashType() != null) {
                finalType = latestCls.getTrashType();
            }
        }

        TrashItemKnowledge finalKnowledge = kn;
        if (finalItem != null) {
            finalKnowledge = knowledgeRepo.findActiveByTrashItemId(finalItem.getId()).orElse(kn);
        }

        return AiResponseDetailsDTO.DetectionDTO.builder()
                .id(det.getId())
                .label(det.getLabel())
                .labelDisplay(det.getLabelDisplay() != null ? det.getLabelDisplay() : det.getLabel())
                .confidence(det.getConfidence() != null ? det.getConfidence() : 0f)
                .annotatedUrl(det.getAnnotatedUrl())
                .status(det.getStatus())

                .x1(det.getX1())
                .y1(det.getY1())
                .x2(det.getX2())
                .y2(det.getY2())
                .cropUrl(det.getCropUrl())

                .trashItemId(finalItem != null ? finalItem.getId() : null)
                .trashType(finalType != null ? finalType.getName() : null)
                .material(finalKnowledge != null ? finalKnowledge.getMaterial() : null)
                .note(finalKnowledge != null ? finalKnowledge.getNote() : null)
                .action(finalKnowledge != null ? finalKnowledge.getAction() : null)
                .detail(finalKnowledge == null ? null : AiResponseDetailsDTO.DetailDTO.builder()
                        .impact(finalKnowledge.getImpact())
                        .toxicity(finalKnowledge.getToxicity())
                        .safeSteps(finalKnowledge.getSafeSteps())
                        .build())
                .build();
    }

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
        return s.isEmpty() ? null : s;
    }
}