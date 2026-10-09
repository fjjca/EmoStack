package org.example.aispingboot.DTO.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmotionLogResponseDTO {
    private Long id;
    private LocalDate diaryDate;
    private String dominantEmotion;
    private Integer moodScore;
    private String emotionTriggers;
    private Integer stressLevel;
    private LocalDateTime recordTime;
    private String aiEmotionAnalysis;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}