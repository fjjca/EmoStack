package org.example.aispingboot.AiService;

import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class RagService implements ApplicationRunner {
    @Autowired
    private VectorStore vectorStore;

    @Value("${rag.folder}")
    private String folderPath;

    @Value("${rag.top-k:4}")
    private int topK;

    @Value("${rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    // 文件名 -> 内容哈希，用于增量对比
    private final Map<String, String> fileSignatures = new ConcurrentHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        try {
            int indexed = scanAndIndex();
            log.info("RAG 知识库初始化完成，共索引 {} 个片段", indexed);
        } catch (Exception e) {
            log.error("RAG 知识库初始化失败（降级为无检索模式）", e);
        }
    }

    public int scanAndIndex() throws IOException {
        Path dir = Paths.get(folderPath);
        if (!Files.isDirectory(dir)) {
            log.warn("知识库文件夹不存在: {}", dir.toAbsolutePath());
            return 0;
        }
        Map<String, String> current = collectFiles(dir);
        int indexed = 0;
        for (String name : current.keySet()) {
            String content = Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
            List<Document> docs = chunkDocument(name, content);
            // 先清掉该文件旧片段，再整体重建，保证幂等（不产生残留/重复）
            deleteSource(name);
            if (!docs.isEmpty()) {
                addInBatches(docs);
                indexed += docs.size();
            }
        }
        fileSignatures.clear();
        fileSignatures.putAll(current);
        log.info("RAG 知识库初始化完成，共索引 {} 个片段（{} 个文件）", indexed, current.size());
        return indexed;
    }

    @Scheduled(fixedDelayString = "${rag.scan-interval-ms:60000}")
    public void incrementalSync() {
        try {
            Path dir = Paths.get(folderPath);
            if (!Files.isDirectory(dir)) {
                return;
            }
            Map<String, String> current = collectFiles(dir);

            // 新增 / 修改：内容哈希变化的文件，先清旧片段再写入新片段
            List<Document> toAdd = new ArrayList<>();
            List<String> changed = new ArrayList<>();
            for (Map.Entry<String, String> entry : current.entrySet()) {
                String name = entry.getKey();
                String oldHash = fileSignatures.get(name);
                if (oldHash == null || !oldHash.equals(entry.getValue())) {
                    String content = Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
                    toAdd.addAll(chunkDocument(name, content));
                    changed.add(name);
                }
            }
            for (String name : changed) {
                deleteSource(name);
            }
            if (!toAdd.isEmpty()) {
                addInBatches(toAdd);
                log.info("RAG 增量同步：新增/更新 {} 个文件的 {} 个片段", changed.size(), toAdd.size());
            }

            // 删除：签名表存在但磁盘已不存在的文件
            List<String> toDelete = new ArrayList<>();
            for (String name : fileSignatures.keySet()) {
                if (!current.containsKey(name)) {
                    toDelete.add(name);
                }
            }
            if (!toDelete.isEmpty()) {
                for (String name : toDelete) {
                    deleteSource(name);
                }
                log.info("RAG 增量同步：删除 {} 个文件: {}", toDelete.size(), toDelete);
            }

            fileSignatures.clear();
            fileSignatures.putAll(current);
        } catch (Exception e) {
            log.error("RAG 增量同步失败", e);
        }
    }

    // 按 ```## 标题``` 切块；无标题时整篇为一块。块 id 用「文件名::序号」，保持稳定便于幂等覆盖
    private List<Document> chunkDocument(String fileName, String content) {
        List<Document> docs = new ArrayList<>();
        String[] lines = String.valueOf(content).split("\n", -1);
        StringBuilder buf = new StringBuilder();
        String heading = null;
        int idx = 0;
        for (String rawLine : lines) {
            String line = rawLine.replaceAll("\\s+$", "");
            if (line.startsWith("## ")) {
                if (buf.length() > 0) {
                    docs.add(buildChunk(fileName, heading, ++idx, buf.toString()));
                    buf.setLength(0);
                }
                heading = line;
                buf.append(line).append("\n");
            } else if (line.startsWith("##") && !line.startsWith("## ")) {
                // ```#### ``` 等更次级标题归入当前块
                buf.append(rawLine).append("\n");
            } else {
                buf.append(rawLine).append("\n");
            }
        }
        if (buf.length() > 0) {
            docs.add(buildChunk(fileName, heading, ++idx, buf.toString()));
        }
        return docs;
    }

    private Document buildChunk(String fileName, String heading, int idx, String content) {
        String id = fileName + "::" + idx;
        String h = heading == null ? fileName : heading.replaceAll("^#+\\s*", "");
        return new Document(id, content, Map.of("source", fileName, "heading", h));
    }

    // 按 source 元数据删除某个文件的全部片段（Chroma where 过滤）
    private void deleteSource(String fileName) {
        try {
            Filter.Expression expr = new FilterExpressionBuilder().eq("source", fileName).build();
            vectorStore.delete(expr);
        } catch (Exception e) {
            log.warn("删除源文件 {} 的旧片段失败（忽略）", fileName, e);
        }
    }

    // 阿里云 embedding 接口单次 batch 上限 25 条，分批写入避免批量向量化被 400 拒绝（知识库片段多时易触发）
    private void addInBatches(List<Document> docs) {
        int batch = 20;
        for (int i = 0; i < docs.size(); i += batch) {
            List<Document> chunkCopy = new ArrayList<>(docs.subList(i, Math.min(i + batch, docs.size())));
            vectorStore.add(chunkCopy);
        }
    }

    private Map<String, String> collectFiles(Path dir) throws IOException {
        Map<String, String> map = new HashMap<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".md") || n.endsWith(".txt");
            }).forEach(p -> {
                try {
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    map.put(p.getFileName().toString(), SecureUtil.sha256(content));
                } catch (IOException e) {
                    log.error("读取文件失败: {}", p, e);
                }
            });
        }
        return map;
    }

    public List<Document> search(String query) {
        try {
            return vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build());
        } catch (Exception e) {
            log.error("RAG 检索失败（降级返回空）", e);
            return List.of();
        }
    }
}
