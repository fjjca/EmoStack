package org.example.aispingboot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.DTO.PageQueryDTO;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.response.ConsultationSessionResponseDTO;
import org.example.aispingboot.entity.ConsultationMessage;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.ConsultationMessageMapper;
import org.example.aispingboot.mapper.ConsultationSessionMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConsultationSessionService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;

    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    public ConsultationSession createSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            ConsultationSession session = ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessionTitle())
                    .startedAt(LocalDateTime.now())
                    .build();
            if (StrUtil.isBlank(createDTO.getSessionTitle())) {
                session.setSessionTitle(String.format("绪栈AI助手 - " + DateUtil.format(LocalDateTime.now(), "MM-dd HH:mm")));
            }
            consultationSessionMapper.insert(session);
            return session;
        }
        return null;
    }

    public Page<ConsultationSessionResponseDTO> sessionPage(PageQueryDTO query, Long userId, boolean isAdmin) {
        LambdaQueryWrapper<ConsultationSession> wrapper = new LambdaQueryWrapper<>();
        if (!isAdmin) {
            wrapper.eq(ConsultationSession::getUserId, userId);
        }
        wrapper.orderByDesc(ConsultationSession::getStartedAt);

        Page<ConsultationSession> page = consultationSessionMapper.selectPage(
                new Page<>(query.resolvePage(), query.resolveSize()), wrapper);

        List<ConsultationSession> sessions = page.getRecords();
        List<Long> sessionIds = sessions.stream().map(ConsultationSession::getId).collect(Collectors.toList());

        Map<Long, Integer> messageCountMap = new HashMap<>();
        Map<Long, ConsultationMessage> lastMessageMap = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            LambdaQueryWrapper<ConsultationMessage> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper.in(ConsultationMessage::getSessionId, sessionIds)
                    .orderByAsc(ConsultationMessage::getCreatedAt)
                    .orderByAsc(ConsultationMessage::getId);
            List<ConsultationMessage> messages = consultationMessageMapper.selectList(messageWrapper);
            for (ConsultationMessage m : messages) {
                messageCountMap.merge(m.getSessionId(), 1, Integer::sum);
                lastMessageMap.put(m.getSessionId(), m);
            }
        }

        Map<Long, User> userMap = buildUserMap(sessions);

        Page<ConsultationSessionResponseDTO> result =
                new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(sessions.stream().map(s -> {
            ConsultationSessionResponseDTO dto = new ConsultationSessionResponseDTO();
            dto.setId(s.getId());
            dto.setUserId(s.getUserId());
            dto.setSessionTitle(s.getSessionTitle());
            dto.setStartedAt(s.getStartedAt());
            User user = userMap.get(s.getUserId());
            if (user != null) {
                dto.setUserNickname(user.getDisplayName());
            }
            dto.setMessageCount(messageCountMap.getOrDefault(s.getId(), 0));
            ConsultationMessage lastMessage = lastMessageMap.get(s.getId());
            if (lastMessage != null) {
                dto.setLastMessageContent(lastMessage.getContent());
                dto.setLastMessageTime(lastMessage.getCreatedAt());
            }
            dto.setDurationMinutes(resolveDurationMinutes(s));
            return dto;
        }).collect(Collectors.toList()));
        return result;
    }

    public void deleteSession(Long sessionId, Long userId, boolean isAdmin) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!isAdmin && !session.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该会话");
        }
        consultationSessionMapper.deleteById(sessionId);
    }

    public ConsultationSession getById(Long sessionId) {
        return consultationSessionMapper.selectById(sessionId);
    }

    public Map<String, Object> getSessionEmotion(Long sessionId) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session != null && StrUtil.isNotBlank(session.getLastEmotionAnalysis())) {
            try {
                return JSONUtil.parseObj(session.getLastEmotionAnalysis());
            } catch (Exception ignored) {
                // fall through to default
            }
        }
        Map<String, Object> defaultEmotion = new HashMap<>();
        defaultEmotion.put("primaryEmotion", "中性");
        defaultEmotion.put("emotionScore", 50);
        defaultEmotion.put("isNegative", false);
        defaultEmotion.put("riskLevel", 0);
        defaultEmotion.put("suggestion", "情绪状态平稳");
        defaultEmotion.put("improvementSuggestions", Collections.emptyList());
        defaultEmotion.put("riskDescription", "");
        return defaultEmotion;
    }

    public void updateEmotionAnalysis(Long sessionId, String analysisJson) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setLastEmotionAnalysis(analysisJson);
            session.setLastEmotionUpdatedAt(LocalDateTime.now());
            consultationSessionMapper.updateById(session);
        }
    }

    private Map<Long, User> buildUserMap(List<ConsultationSession> sessions) {
        List<Long> userIds = sessions.stream()
                .map(ConsultationSession::getUserId)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private Integer resolveDurationMinutes(ConsultationSession session) {
        // 会话表暂无明确结束时间；返回 null 由前端兜底为 0
        return null;
    }
}