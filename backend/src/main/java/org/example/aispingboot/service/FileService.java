package org.example.aispingboot.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.example.aispingboot.DTO.response.FileUploadResponseDTO;
import org.example.aispingboot.common.ResultCode;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.SysFileInfoMapper;
import org.example.aispingboot.entity.SysFileInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    private SysFileInfoMapper sysFileInfoMapper;

    public FileUploadResponseDTO upload(MultipartFile file, String businessType, String businessId,
                                        String businessField, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String extension = StrUtil.isNotBlank(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1) : "";
        extension = extension.toLowerCase();

        String storedName = UUID.randomUUID().toString().replace("-", "") + (StrUtil.isNotBlank(extension) ? "." + extension : "");
        String subDir = LocalDateTime.now().toLocalDate().toString().replace("-", File.separator);

        File dir = new File(new File(uploadDir).getAbsolutePath(), subDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File dest = new File(dir, storedName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }

        String urlPath = "/uploads/" + subDir.replace(File.separatorChar, '/') + "/" + storedName;

        SysFileInfo info = new SysFileInfo();
        info.setOriginalName(originalName);
        info.setFilePath(urlPath);
        info.setFileSize(file.getSize());
        info.setFileType(resolveFileType(extension));
        info.setBusinessType(businessType);
        info.setBusinessId(businessId);
        info.setBusinessField(businessField);
        info.setUploadUserId(userId);
        info.setIsTemp(1);
        info.setStatus(1);
        info.setCreateTime(LocalDateTime.now());
        sysFileInfoMapper.insert(info);

        FileUploadResponseDTO dto = new FileUploadResponseDTO();
        dto.setFileName(originalName);
        dto.setFilePath(urlPath);
        dto.setFileType(info.getFileType());
        dto.setFileSize(info.getFileSize());
        return dto;
    }

    private String resolveFileType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "IMG";
            case "pdf" -> "PDF";
            case "txt", "md" -> "TXT";
            case "doc", "docx" -> "DOC";
            case "xls", "xlsx" -> "XLS";
            default -> StrUtil.isBlank(extension) ? "FILE" : extension.toUpperCase();
        };
    }
}