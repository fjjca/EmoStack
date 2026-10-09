package org.example.aispingboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmotionDiaryCreateDTO {

    private LocalDate diaryDate;

    @Size(max = 2000, message = "日记内容过长")
    private String diaryContent;

    @Min(value = 1, message = "睡眠质量范围1-5")
    @Max(value = 5, message = "睡眠质量范围1-5")
    private Integer sleepQuality;

    /** 是否将新增内容追加到当天已有感想之后（true=追加，false/空=覆盖） */
    private Boolean append;
}