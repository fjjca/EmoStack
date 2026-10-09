package org.example.aispingboot.AiService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class BoundaryLoader {
    @Value("${rag.boundary-file:../system_boundary.md}")
    private String boundaryFilePath;

    private volatile String boundaryContent;

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 60000)
    public void refresh() {
        try {
            Path path = Paths.get(boundaryFilePath);
            if (Files.exists(path)) {
                boundaryContent = Files.readString(path, StandardCharsets.UTF_8);
            } else {
                log.warn("边界约束文件不存在: {}，本次跳过注入", path.toAbsolutePath());
                boundaryContent = null;
            }
        } catch (Exception e) {
            log.error("读取边界约束文件失败: {}", boundaryFilePath, e);
            boundaryContent = null;
        }
    }

    public String getBoundaryContent() {
        return boundaryContent;
    }
}
