package org.example.aispingboot.DTO.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmotionDiaryAdminResponseDTO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private LocalDate diaryDate;
    private Integer moodScore;
    private String dominantEmotion;
    private String emotionTriggers;
    private String diaryContent;
    private Integer sleepQuality;
    private Integer stressLevel;
    private LocalDateTime recordTime;
    private String aiEmotionAnalysis;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}