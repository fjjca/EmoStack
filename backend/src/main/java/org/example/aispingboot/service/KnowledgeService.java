package org.example.aispingboot.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.aispingboot.DTO.command.KnowledgeArticlePageQueryDTO;
import org.example.aispingboot.DTO.response.KnowledgeArticleResponseDTO;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeCategoryMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeArticleMapper articleMapper;

    @Autowired
    private KnowledgeCategoryMapper categoryMapper;

    @Autowired
    private UserMapper userMapper;

    public List<KnowledgeCategory> categoryTree() {
        LambdaQueryWrapper<KnowledgeCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCategory::getStatus, 1)
                .orderByAsc(KnowledgeCategory::getSortOrder);
        return categoryMapper.selectList(wrapper);
    }

    public Page<KnowledgeArticleResponseDTO> articlePage(KnowledgeArticlePageQueryDTO query) {
        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getTitle())) {
            wrapper.like(KnowledgeArticle::getTitle, query.getTitle());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(KnowledgeArticle::getCategoryId, query.getCategoryId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(KnowledgeArticle::getStatus, query.getStatus());
        }

        String sortField = query.getSortField();
        boolean asc = "asc".equalsIgnoreCase(query.getSortDirection());
        if ("publishedAt".equals(sortField)) {
            wrapper.orderBy(true, asc, KnowledgeArticle::getPublishedAt);
        } else if ("readCount".equals(sortField)) {
            wrapper.orderBy(true, asc, KnowledgeArticle::getReadCount);
        } else {
            wrapper.orderByDesc(KnowledgeArticle::getUpdatedAt);
        }

        Page<KnowledgeArticle> page = articleMapper.selectPage(
                new Page<>(query.resolvePage(), query.resolveSize()), wrapper);

        Map<Long, String> categoryNameMap = buildCategoryNameMap(page.getRecords());
        Map<Long, String> authorNameMap = buildAuthorNameMap(page.getRecords());

        Page<KnowledgeArticleResponseDTO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(a -> toResponse(a, categoryNameMap, authorNameMap))
                .collect(Collectors.toList()));
        return result;
    }

    public KnowledgeArticleResponseDTO getArticle(String id) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        Map<Long, String> categoryNameMap = buildCategoryNameMap(Collections.singletonList(article));
        Map<Long, String> authorNameMap = buildAuthorNameMap(Collections.singletonList(article));
        return toResponse(article, categoryNameMap, authorNameMap);
    }

    public KnowledgeArticle createArticle(KnowledgeArticleCommandDTO dto, Long authorId) {
        String id = StrUtil.isBlank(dto.getId()) ? UUID.randomUUID().toString() : dto.getId();
        if (articleMapper.selectById(id) != null) {
            throw new BusinessException("文章ID已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(id);
        article.setTitle(dto.getTitle());
        article.setCategoryId(dto.getCategoryId());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCoverImage(dto.getCoverImage());
        article.setTags(dto.getTags());
        article.setAuthorId(authorId);
        article.setReadCount(0);
        article.setStatus(0);
        article.setCreatedAt(now);
        article.setUpdatedAt(now);

        articleMapper.insert(article);
        return article;
    }

    public void updateArticle(String id, KnowledgeArticleCommandDTO dto) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        article.setTitle(dto.getTitle());
        article.setCategoryId(dto.getCategoryId());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCoverImage(dto.getCoverImage());
        article.setTags(dto.getTags());
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    public void updateStatus(String id, Integer status) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        article.setStatus(status);
        if (status != null && status == 1 && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    public void deleteArticle(String id) {
        if (articleMapper.selectById(id) == null) {
            throw new BusinessException("文章不存在");
        }
        articleMapper.deleteById(id);
    }

    private Map<Long, String> buildCategoryNameMap(List<KnowledgeArticle> articles) {
        List<Long> categoryIds = articles.stream()
                .map(KnowledgeArticle::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(KnowledgeCategory::getId, KnowledgeCategory::getCategoryName, (a, b) -> a));
    }

    private Map<Long, String> buildAuthorNameMap(List<KnowledgeArticle> articles) {
        List<Long> authorIds = articles.stream()
                .map(KnowledgeArticle::getAuthorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }

    private KnowledgeArticleResponseDTO toResponse(KnowledgeArticle article,
                                                   Map<Long, String> categoryNameMap,
                                                   Map<Long, String> authorNameMap) {
        KnowledgeArticleResponseDTO dto = new KnowledgeArticleResponseDTO();
        dto.setId(article.getId());
        dto.setCategoryId(article.getCategoryId());
        dto.setCategoryName(article.getCategoryId() == null ? null : categoryNameMap.get(article.getCategoryId()));
        dto.setTitle(article.getTitle());
        dto.setSummary(article.getSummary());
        dto.setContent(article.getContent());
        dto.setCoverImage(article.getCoverImage());
        dto.setTags(article.getTags());
        dto.setTagArray(parseTags(article.getTags()));
        dto.setAuthorId(article.getAuthorId());
        dto.setAuthorName(article.getAuthorId() == null ? null : authorNameMap.get(article.getAuthorId()));
        dto.setReadCount(article.getReadCount());
        dto.setStatus(article.getStatus());
        dto.setPublishedAt(article.getPublishedAt());
        dto.setCreatedAt(article.getCreatedAt());
        dto.setUpdatedAt(article.getUpdatedAt());
        return dto;
    }

    private List<String> parseTags(String tags) {
        if (StrUtil.isBlank(tags)) {
            return new ArrayList<>();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}