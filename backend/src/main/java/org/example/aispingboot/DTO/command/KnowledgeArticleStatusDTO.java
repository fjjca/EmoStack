package org.example.aispingboot.DTO.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeArticleStatusDTO {
    @NotNull(message = "状态不能为空")
    private Integer status;
}