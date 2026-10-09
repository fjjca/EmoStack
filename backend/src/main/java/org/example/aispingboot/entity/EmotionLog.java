package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("emotion_log")
public class EmotionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("diary_date")
    private LocalDate diaryDate;

    @TableField("dominant_emotion")
    private String dominantEmotion;

    @TableField("mood_score")
    private Integer moodScore;

    @TableField("emotion_triggers")
    private String emotionTriggers;

    @TableField("stress_level")
    private Integer stressLevel;

    @TableField("record_time")
    private LocalDateTime recordTime;

    @TableField("ai_emotion_analysis")
    private String aiEmotionAnalysis;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}