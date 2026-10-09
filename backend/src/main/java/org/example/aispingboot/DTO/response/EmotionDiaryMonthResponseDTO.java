package org.example.aispingboot.DTO.response;

import lombok.Data;

import java.util.List;

@Data
public class EmotionDiaryMonthResponseDTO {
    private List<EmotionDiaryResponseDTO> diaries;
    private List<EmotionLogResponseDTO> logs;
}