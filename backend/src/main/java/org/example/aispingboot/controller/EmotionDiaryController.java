package org.example.aispingboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.example.aispingboot.DTO.PageQueryDTO;
import org.example.aispingboot.DTO.command.EmotionDiaryAdminPageQueryDTO;
import org.example.aispingboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.aispingboot.DTO.command.EmotionLogCreateDTO;
import org.example.aispingboot.DTO.response.EmotionDiaryAdminResponseDTO;
import org.example.aispingboot.DTO.response.EmotionDiaryMonthResponseDTO;
import org.example.aispingboot.DTO.response.EmotionDiaryResponseDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.entity.EmotionLog;
import org.example.aispingboot.service.EmotionDiaryService;
import org.example.aispingboot.util.CurrentUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {

    @Autowired
    private EmotionDiaryService diaryService;

    /** 保存/更新每日日记（一天一条，可追加/覆盖感想） */
    @PostMapping
    public Result<EmotionDiary> saveDiary(@Valid @RequestBody EmotionDiaryCreateDTO dto) {
        return Result.ok(diaryService.saveDiary(dto, CurrentUserUtil.getUserId()));
    }

    /** 新增一条情绪记录（同一天可多条，支持自定义记录时刻） */
    @PostMapping("/log")
    public Result<EmotionLog> addEmotionLog(@Valid @RequestBody EmotionLogCreateDTO dto) {
        return Result.ok(diaryService.addEmotionLog(dto, CurrentUserUtil.getUserId()));
    }

    @GetMapping("/my")
    public Result<Page<EmotionDiaryResponseDTO>> myPage(PageQueryDTO query) {
        return Result.ok(diaryService.myPage(CurrentUserUtil.getUserId(), query));
    }

    /** 月度概述：每日日记 + 全部情绪记录 */
    @GetMapping("/month")
    public Result<EmotionDiaryMonthResponseDTO> month(@RequestParam String yearMonth) {
        return Result.ok(diaryService.getMonth(CurrentUserUtil.getUserId(), yearMonth));
    }

    @GetMapping("/admin/page")
    public Result<Page<EmotionDiaryAdminResponseDTO>> adminPage(EmotionDiaryAdminPageQueryDTO query) {
        return Result.ok(diaryService.adminPage(query));
    }

    @DeleteMapping("/admin/{id}")
    public Result<Void> deleteLog(@PathVariable Long id) {
        diaryService.deleteLog(id);
        return Result.ok();
    }
}