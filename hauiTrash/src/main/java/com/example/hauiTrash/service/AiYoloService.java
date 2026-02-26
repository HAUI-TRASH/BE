package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiPredictRequestDTO;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;

public interface AiYoloService {
    YoloPredictResponseDTO predictAndSave(AiPredictRequestDTO req);
}
