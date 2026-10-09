package org.example.aispingboot.DTO;

import lombok.Data;

@Data
public class PageQueryDTO {
    private Integer current;
    private Integer currentPage;
    private Integer pageNum;
    private Integer size;
    private Integer pageSize;

    public Integer resolvePage() {
        if (current != null && current > 0) return current;
        if (currentPage != null && currentPage > 0) return currentPage;
        if (pageNum != null && pageNum > 0) return pageNum;
        return 1;
    }

    public Integer resolveSize() {
        if (size != null && size > 0) return size;
        if (pageSize != null && pageSize > 0) return pageSize;
        return 10;
    }
}