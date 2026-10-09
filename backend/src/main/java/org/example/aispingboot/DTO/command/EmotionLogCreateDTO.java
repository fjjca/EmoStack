package org.example.aispingboot.DTO.command;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmotionLogCreateDTO {

    private LocalDate diaryDate;

    /** 记录时刻，可空；为空则默认为当前时间。支持晚点补记 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;

    @NotBlank(message = "主要情绪不能为空")
    @Size(max = 50, message = "主要情绪过长")
    private String dominantEmotion;

    @NotNull(message = "情绪评分不能为空")
    @Min(value = 1, message = "情绪评分范围1-10")
    @Max(value = 10, message = "情绪评分范围1-10")
    private Integer moodScore;

    @Size(max = 1000, message = "情绪触发因素过长")
    private String emotionTriggers;

    @Min(value = 1, message = "压力水平范围1-5")
    @Max(value = 5, message = "压力水平范围1-5")
    private Integer stressLevel;
}