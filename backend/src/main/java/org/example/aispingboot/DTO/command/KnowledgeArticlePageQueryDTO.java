package org.example.aispingboot.DTO.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.aispingboot.DTO.PageQueryDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeArticlePageQueryDTO extends PageQueryDTO {
    private String title;
    private Long categoryId;
    private Integer status;
    private String sortField;
    private String sortDirection;
}