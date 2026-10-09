package org.example.aispingboot.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.DTO.PageQueryDTO;
import org.example.aispingboot.DTO.command.EmotionDiaryAdminPageQueryDTO;
import org.example.aispingboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.aispingboot.DTO.command.EmotionLogCreateDTO;
import org.example.aispingboot.DTO.response.EmotionDiaryAdminResponseDTO;
import org.example.aispingboot.DTO.response.EmotionDiaryMonthResponseDTO;
import org.example.aispingboot.DTO.response.EmotionDiaryResponseDTO;
import org.example.aispingboot.DTO.response.EmotionLogResponseDTO;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.entity.EmotionLog;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.EmotionDiaryMapper;
import org.example.aispingboot.mapper.EmotionLogMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmotionDiaryService {

    @Autowired
    private EmotionDiaryMapper diaryMapper;

    @Autowired
    private EmotionLogMapper logMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 保存/更新每日日记（一天一条）：sleepQuality 覆盖更新，diaryContent 按 append 决定追加或覆盖。
     */
    public EmotionDiary saveDiary(EmotionDiaryCreateDTO dto, Long userId) {
        LocalDate date = dto.getDiaryDate() != null ? dto.getDiaryDate() : LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        EmotionDiary exist = diaryMapper.selectOne(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .eq(EmotionDiary::getDiaryDate, date));

        if (exist != null) {
            if (StrUtil.isNotBlank(dto.getDiaryContent())) {
                String base = exist.getDiaryContent();
                boolean append = Boolean.TRUE.equals(dto.getAppend());
                if (append && StrUtil.isNotBlank(base)) {
                    exist.setDiaryContent(base + "\n" + dto.getDiaryContent());
                } else {
                    exist.setDiaryContent(dto.getDiaryContent());
                }
            }
            if (dto.getSleepQuality() != null) {
                exist.setSleepQuality(dto.getSleepQuality());
            }
            exist.setUpdatedAt(now);
            diaryMapper.updateById(exist);
            return exist;
        }

        EmotionDiary diary = new EmotionDiary();
        diary.setUserId(userId);
        diary.setDiaryDate(date);
        diary.setDiaryContent(dto.getDiaryContent());
        diary.setSleepQuality(dto.getSleepQuality());
        diary.setCreatedAt(now);
        diary.setUpdatedAt(now);
        diaryMapper.insert(diary);
        return diary;
    }

    /**
     * 新增一条情绪记录（同一天可多条），支持自定义记录时刻。
     */
    public EmotionLog addEmotionLog(EmotionLogCreateDTO dto, Long userId) {
        LocalDate date = dto.getDiaryDate();
        if (date == null && dto.getRecordTime() != null) {
            date = dto.getRecordTime().toLocalDate();
        }
        if (date == null) {
            date = LocalDate.now();
        }
        LocalDateTime now = LocalDateTime.now();

        EmotionLog log = new EmotionLog();
        log.setUserId(userId);
        log.setDiaryDate(date);
        log.setDominantEmotion(dto.getDominantEmotion());
        log.setMoodScore(dto.getMoodScore());
        log.setEmotionTriggers(dto.getEmotionTriggers());
        log.setStressLevel(dto.getStressLevel());
        log.setRecordTime(dto.getRecordTime() != null ? dto.getRecordTime() : now);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        logMapper.insert(log);
        return log;
    }

    /**
     * 取某个用户某年的月度概述（每日日记 + 全部情绪记录）。
     */
    public EmotionDiaryMonthResponseDTO getMonth(Long userId, String yearMonth) {
        String[] parts = yearMonth.split("-");
        int year = Integer.parseInt(parts[0].trim());
        int month = Integer.parseInt(parts[1].trim());
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);

        List<EmotionDiary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .ge(EmotionDiary::getDiaryDate, start)
                .lt(EmotionDiary::getDiaryDate, end)
                .orderByAsc(EmotionDiary::getDiaryDate));

        List<EmotionLog> logs = logMapper.selectList(new LambdaQueryWrapper<EmotionLog>()
                .eq(EmotionLog::getUserId, userId)
                .ge(EmotionLog::getDiaryDate, start)
                .lt(EmotionLog::getDiaryDate, end)
                .orderByAsc(EmotionLog::getRecordTime));

        EmotionDiaryMonthResponseDTO result = new EmotionDiaryMonthResponseDTO();
        result.setDiaries(diaries.stream().map(this::toDiaryResponse).collect(Collectors.toList()));
        result.setLogs(logs.stream().map(this::toLogResponse).collect(Collectors.toList()));
        return result;
    }

    public Page<EmotionDiaryResponseDTO> myPage(Long userId, PageQueryDTO query) {
        LambdaQueryWrapper<EmotionDiary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmotionDiary::getUserId, userId)
                .orderByDesc(EmotionDiary::getDiaryDate)
                .orderByDesc(EmotionDiary::getCreatedAt);

        Page<EmotionDiary> page = diaryMapper.selectPage(
                new Page<>(query.resolvePage(), query.resolveSize()), wrapper);

        Page<EmotionDiaryResponseDTO> result =
                new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(this::toDiaryResponse)
                .collect(Collectors.toList()));
        return result;
    }

    public Page<EmotionDiaryAdminResponseDTO> adminPage(EmotionDiaryAdminPageQueryDTO query) {
        LambdaQueryWrapper<EmotionLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(EmotionLog::getUserId, query.getUserId());
        }
        if (StrUtil.isNotBlank(query.getMoodScreRange())) {
            int[] range = parseRange(query.getMoodScreRange());
            if (range != null) {
                wrapper.between(EmotionLog::getMoodScore, range[0], range[1]);
            }
        }
        wrapper.orderByDesc(EmotionLog::getDiaryDate).orderByDesc(EmotionLog::getCreatedAt);

        Page<EmotionLog> page = logMapper.selectPage(
                new Page<>(query.resolvePage(), query.resolveSize()), wrapper);

        Map<Long, User> userMap = buildUserMap(page.getRecords().stream()
                .map(EmotionLog::getUserId).collect(Collectors.toList()));

        Page<EmotionDiaryAdminResponseDTO> result =
                new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(l -> toAdminResponse(l, userMap))
                .collect(Collectors.toList()));
        return result;
    }

    public void deleteLog(Long id) {
        if (logMapper.selectById(id) == null) {
            throw new BusinessException("情绪记录不存在");
        }
        logMapper.deleteById(id);
    }

    private EmotionDiaryResponseDTO toDiaryResponse(EmotionDiary diary) {
        EmotionDiaryResponseDTO dto = new EmotionDiaryResponseDTO();
        dto.setId(diary.getId());
        dto.setDiaryDate(diary.getDiaryDate());
        dto.setDiaryContent(diary.getDiaryContent());
        dto.setSleepQuality(diary.getSleepQuality());
        dto.setCreatedAt(diary.getCreatedAt());
        dto.setUpdatedAt(diary.getUpdatedAt());
        return dto;
    }

    private EmotionLogResponseDTO toLogResponse(EmotionLog log) {
        EmotionLogResponseDTO dto = new EmotionLogResponseDTO();
        dto.setId(log.getId());
        dto.setDiaryDate(log.getDiaryDate());
        dto.setDominantEmotion(log.getDominantEmotion());
        dto.setMoodScore(log.getMoodScore());
        dto.setEmotionTriggers(log.getEmotionTriggers());
        dto.setStressLevel(log.getStressLevel());
        dto.setRecordTime(log.getRecordTime());
        dto.setAiEmotionAnalysis(log.getAiEmotionAnalysis());
        dto.setCreatedAt(log.getCreatedAt());
        dto.setUpdatedAt(log.getUpdatedAt());
        return dto;
    }

    private int[] parseRange(String moodScreRange) {
        if (StrUtil.isBlank(moodScreRange) || !moodScreRange.contains("-")) {
            return null;
        }
        try {
            String[] parts = moodScreRange.split("-");
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Long, User> buildUserMap(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private EmotionDiaryAdminResponseDTO toAdminResponse(EmotionLog log, Map<Long, User> userMap) {
        EmotionDiaryAdminResponseDTO dto = new EmotionDiaryAdminResponseDTO();
        dto.setId(log.getId());
        dto.setUserId(log.getUserId());
        User user = userMap.get(log.getUserId());
        if (user != null) {
            dto.setUsername(user.getUsername());
            dto.setNickname(user.getNickname() != null ? user.getNickname() : user.getUsername());
        }
        dto.setDiaryDate(log.getDiaryDate());
        dto.setMoodScore(log.getMoodScore());
        dto.setDominantEmotion(log.getDominantEmotion());
        dto.setEmotionTriggers(log.getEmotionTriggers());
        dto.setStressLevel(log.getStressLevel());
        dto.setRecordTime(log.getRecordTime());
        dto.setAiEmotionAnalysis(log.getAiEmotionAnalysis());
        dto.setCreatedAt(log.getCreatedAt());
        dto.setUpdatedAt(log.getUpdatedAt());
        return dto;
    }
}