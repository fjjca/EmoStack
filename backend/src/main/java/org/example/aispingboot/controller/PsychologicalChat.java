package org.example.aispingboot.controller;

import cn.hutool.json.JSONUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.validation.Valid;
import org.example.aispingboot.AiService.PsychologicalSupportService;
import org.example.aispingboot.AiService.StructOutPut;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.command.ConsultationStreamDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.common.ResultCode;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.result.view.Fragment;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.DTO.PageQueryDTO;
import org.example.aispingboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispingboot.DTO.response.ConsultationSessionResponseDTO;
import org.example.aispingboot.service.ConsultationMessageService;
import org.example.aispingboot.service.ConsultationSessionService;
import org.example.aispingboot.util.CurrentUserUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChat {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;

    @Autowired
    private ConsultationSessionService consultationSessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSessionCreateDTO createDTO) {
        // 获取当前用户
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        StructOutPut.StreamChatSession session = psychologicalSupportService.startSession(userId, createDTO);
        return Result.ok(session);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationStreamDTO streamDTO) {
        // 获取当前用户
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();

        if (userId == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg(), "用户未登录")))
                    .build());
        }

        // 开始流式对话
        return psychologicalSupportService.streamPsychologicalChat(streamDTO.getSessionId(), streamDTO.getUserMessage())
                .map(Fragment -> {
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(JSONUtil.toJsonStr(Result.ok(Map.of("content", Fragment, "type", "normal"))))
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()
                ))
                .delayElements(Duration.ofMillis(50)); // 添加延迟确保流式数据的体验
    }

    @GetMapping("/sessions")
    public Result<Page<ConsultationSessionResponseDTO>> sessionPage(PageQueryDTO query) {
        Long userId = CurrentUserUtil.getUserId();
        boolean isAdmin = CurrentUserUtil.isAdmin();
        return Result.ok(consultationSessionService.sessionPage(query, userId, isAdmin));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> sessionMessages(@PathVariable Long sessionId) {
        return Result.ok(consultationMessageService.listBySessionId(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = CurrentUserUtil.getUserId();
        boolean isAdmin = CurrentUserUtil.isAdmin();
        consultationSessionService.deleteSession(sessionId, userId, isAdmin);
        return Result.ok();
    }

    @GetMapping("/session/{sessionId}/emotion")
    public Result<Map<String, Object>> sessionEmotion(@PathVariable String sessionId) {
        Long dbSessionId = parseSessionId(sessionId);
        return Result.ok(consultationSessionService.getSessionEmotion(dbSessionId));
    }

    private Long parseSessionId(String sessionId) {
        String id = sessionId;
        if (id != null && id.startsWith("session_")) {
            id = id.substring("session_".length());
        }
        return Long.valueOf(id);
    }
}
