package org.example.aispingboot.controller;

import org.example.aispingboot.AiService.RagService;
import org.example.aispingboot.common.Result;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    @Autowired
    private RagService ragService;

    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestParam String q) {
        List<Document> docs = ragService.search(q);
        List<Map<String, Object>> list = docs.stream().map(d -> Map.of(
                "id", d.getId(),
                "source", d.getMetadata().getOrDefault("source", ""),
                "score", d.getScore() == null ? 0.0 : d.getScore(),
                "content", d.getText()
        )).toList();
        return Result.ok(list);
    }
}
