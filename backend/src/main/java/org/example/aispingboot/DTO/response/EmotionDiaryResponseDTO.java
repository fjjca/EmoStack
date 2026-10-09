package org.example.aispingboot.DTO.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmotionDiaryResponseDTO {
    private Long id;
    private LocalDate diaryDate;
    private String diaryContent;
    private Integer sleepQuality;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}