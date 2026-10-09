package org.example.aispingboot.DTO.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.aispingboot.DTO.PageQueryDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmotionDiaryAdminPageQueryDTO extends PageQueryDTO {
    private Long userId;
    private String moodScreRange;
}