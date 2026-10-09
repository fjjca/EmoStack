package org.example.aispingboot.DTO.response;

import lombok.Data;

@Data
public class FileUploadResponseDTO {
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
}