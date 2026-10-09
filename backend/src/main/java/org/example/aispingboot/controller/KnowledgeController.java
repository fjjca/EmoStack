package org.example.aispingboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.example.aispingboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.aispingboot.DTO.command.KnowledgeArticlePageQueryDTO;
import org.example.aispingboot.DTO.command.KnowledgeArticleStatusDTO;
import org.example.aispingboot.DTO.response.KnowledgeArticleResponseDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.service.KnowledgeService;
import org.example.aispingboot.util.CurrentUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @GetMapping("/category/tree")
    public Result<List<KnowledgeCategory>> categoryTree() {
        return Result.ok(knowledgeService.categoryTree());
    }

    @GetMapping("/article/page")
    public Result<Page<KnowledgeArticleResponseDTO>> articlePage(KnowledgeArticlePageQueryDTO query) {
        return Result.ok(knowledgeService.articlePage(query));
    }

    @GetMapping("/article/{id}")
    public Result<KnowledgeArticleResponseDTO> getArticle(@PathVariable String id) {
        return Result.ok(knowledgeService.getArticle(id));
    }

    @PostMapping("/article")
    public Result<KnowledgeArticle> createArticle(@Valid @RequestBody KnowledgeArticleCommandDTO dto) {
        return Result.ok(knowledgeService.createArticle(dto, CurrentUserUtil.getUserId()));
    }

    @PutMapping("/article/{id}")
    public Result<Void> updateArticle(@PathVariable String id, @Valid @RequestBody KnowledgeArticleCommandDTO dto) {
        knowledgeService.updateArticle(id, dto);
        return Result.ok();
    }

    @PutMapping("/article/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestBody KnowledgeArticleStatusDTO dto) {
        knowledgeService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/article/{id}")
    public Result<Void> deleteArticle(@PathVariable String id) {
        knowledgeService.deleteArticle(id);
        return Result.ok();
    }
}