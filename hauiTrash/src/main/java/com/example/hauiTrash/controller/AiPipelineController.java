package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.KnowledgeViewDTO;
import com.example.hauiTrash.service.AiPipelineService;
import com.example.hauiTrash.service.TrashKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiPipelineController {
    @Autowired
    private AiPipelineService aiPipelineService;
    @Autowired
    private TrashKnowledgeService knowledgeService;
    @PostMapping("/ai_requests/{id}/predict")
    public ResponseEntity<AiResponseDetailsDTO> predict(@PathVariable Integer id) {
        return ResponseEntity.ok(aiPipelineService.predictAndEnrich(id));
    }

    @GetMapping("/ai_response/{id}/detail")
    public ResponseEntity<AiResponseDetailsDTO> getDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(aiPipelineService.getDetail(id));
    }
    @GetMapping("/api/trash-items/{trashItemId}/knowledge")
    public ResponseEntity<KnowledgeViewDTO> getKnowledge(@PathVariable Integer trashItemId) {
        KnowledgeViewDTO dto = knowledgeService.getKnowledgeIfExists(trashItemId);
        if (dto == null) return ResponseEntity.noContent().build(); // 204
        return ResponseEntity.ok(dto); // 200
    }
    @PostMapping("/api/trash-items/{trashItemId}/knowledge:generate")
    public KnowledgeViewDTO generate(@PathVariable Integer trashItemId,
                                     @RequestParam(defaultValue = "false") boolean force) {
        return knowledgeService.generateKnowledge(trashItemId, force);
    }

}