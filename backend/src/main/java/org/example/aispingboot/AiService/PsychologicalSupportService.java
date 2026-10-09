package org.example.aispingboot.AiService;

import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.service.ConsultationMessageService;
import org.example.aispingboot.service.ConsultationSessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PsychologicalSupportService {
    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ConsultationSessionService consultationSessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    @Autowired
    private BoundaryLoader boundaryLoader;

    @Autowired
    private RagRouter ragRouter;

    @Autowired
    private RagService ragService;

    public StructOutPut.StreamChatSession startSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        // 创建数据库会话记录
        ConsultationSession consultationSession = consultationSessionService.createSession(userId, createDTO);

        // 将初始用户消息保存到Message表
        consultationMessageService.saveUserMessage(consultationSession.getId(), createDTO.getInitialMessage(), null);

        // 创建会话信息
        String sessionId = "session_" + consultationSession.getId();
        return new StructOutPut.StreamChatSession(
                sessionId,
                userId,
                createDTO.getInitialMessage(),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 86400000L, // 24小时
                1,
                "ACTIVE"
        );
    }

    public Flux<String> streamPsychologicalChat(String sessionId, String userMessage) {
        // 创建响应流
        return Flux.create(sink -> {
            // sink.next("数据1") // 发布数据
            // sink.complete(); // 完成流
            // sink.error(exception); // 发布错误
            Long dbSessionId = extractSessionId(sessionId);
            if (dbSessionId == null) {
                sink.error(new RuntimeException("会话ID格式错误"));
                return;
            }
            // 是否为初始消息
            boolean isInitialMessage = false;
            // 检查是否为初始消息，避免重复保存
            Integer messageCount = consultationMessageService.getMessageCountBySessionId(dbSessionId);
            if (messageCount == 1) {
               ConsultationMessageResponseDTO lastMessage = consultationMessageService.getLastMessageBySessionId(dbSessionId);
                if (lastMessage != null && lastMessage.getSenderType() == 1 && userMessage.equals(lastMessage.getContent())) {
                    isInitialMessage = true;
                }
            }
            if (!isInitialMessage) {
                // 保存用户消息到数据库
                consultationMessageService.saveUserMessage(dbSessionId, userMessage, null);
            }

            // 进行流式对话
            // 生成对话记忆管理
            String conversationId = "conversation_" + sessionId;
            // 构建系统提示词
            List<Message> userMessages = new ArrayList<>();
            userMessages.add(new UserMessage(userMessage));
            chatMemory.add(conversationId, userMessages);
            List<Message> systemMessages = new ArrayList<>();
            systemMessages.add(new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT));
            // RAG：路由判断 + 检索注入（置于边界约束之前，边界优先级最高）
            try {
                List<Message> recentMessages = chatMemory.get(conversationId);
                if (ragRouter.needsRag(userMessage, recentMessages)) {
                    List<Document> docs = ragService.search(buildSearchQuery(userMessage, recentMessages));
                    if (!docs.isEmpty()) {
                        systemMessages.add(new SystemMessage(PromptManage.buildRagContext(docs)));
                        log.info("RAG 命中 {} 条知识片段，已注入本轮回答", docs.size());
                    } else {
                        systemMessages.add(new SystemMessage(PromptManage.RAG_MISS_PROMPT));
                        log.info("RAG 未命中知识片段，已注入未命中提示");
                    }
                }
            } catch (Exception e) {
                log.warn("RAG 路由或检索异常，本轮走原对话流程: {}", e.getMessage());
            }
            String boundary = boundaryLoader.getBoundaryContent();
            if (boundary != null && !boundary.isBlank()) {
                systemMessages.add(new SystemMessage(boundary));
            }
            Prompt prompt = new Prompt(systemMessages);

            //用于存储AI完成的响应
            StringBuilder fullResponse = new StringBuilder();

            // 使用chatClient发送消息到Open AI
            chatClient.prompt(prompt)
                    .user(userMessage)
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .doOnNext(Fragment -> {
                        fullResponse.append(Fragment);
                        sink.next(Fragment);
                    })
                    .doOnComplete(() -> {
                        String completeRes = fullResponse.toString();
                        // 将AI返回的内容保存到数据库
                        consultationMessageService.saveAimessage(dbSessionId, completeRes, "openai");
                        // 添加AI回复到chatMemory
                        List<Message> aiMessages = new ArrayList<>();
                        aiMessages.add(new AssistantMessage(completeRes));
                        chatMemory.add(conversationId, aiMessages);
                        // 分析情绪并保存
                        analyzeEmotion(dbSessionId);

                        sink.complete();
                    })
                    .doOnError(error -> {
                        sink.error(error);
                    })
                    .subscribe(); // 订阅并启动流
        });
    }

    // 分析会话情绪并写入数据库
    public void analyzeEmotion(Long dbSessionId) {
        try {
            List<ConsultationMessageResponseDTO> messages = consultationMessageService.listBySessionId(dbSessionId);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (ConsultationMessageResponseDTO msg : messages) {
                String role = (msg.getSenderType() != null && msg.getSenderType() == 1) ? "用户" : "AI助手";
                sb.append(role).append("：").append(msg.getContent()).append("\n");
            }
            String conversationText = sb.toString();

            ChatClient emotionClient = ChatClient.builder(openAiChatModel).build();
            String raw = emotionClient.prompt()
                    .system(PromptManage.EMOTION_ANALYSIS_SYSTEM_PROMPT)
                    .user(conversationText)
                    .call()
                    .content();

            String json = extractJson(raw);
            if (json != null && !json.trim().isEmpty()) {
                consultationSessionService.updateEmotionAnalysis(dbSessionId, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 从AI返回内容中提取JSON
    private String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    // 获取参数中的sessionId
    public Long extractSessionId(String sessionId) {
        if (sessionId != null && sessionId.startsWith("session_")) {
            String idStr = sessionId.substring("session_".length());
            return Long.parseLong(idStr);
        }
        return null;
    }

    // 构造检索 query：结合最近几轮对话上下文（指代词如"这种感觉"需要上文才能定位主题），当前消息已在 recentMessages 末尾
    private String buildSearchQuery(String userMessage, List<Message> recentMessages) {
        if (recentMessages == null || recentMessages.size() <= 1) {
            return userMessage;
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, recentMessages.size() - 4); // 最近 4 条（约 2 轮对话）
        for (int i = start; i < recentMessages.size(); i++) {
            Message m = recentMessages.get(i);
            String text = (m instanceof UserMessage um) ? um.getText()
                    : (m instanceof AssistantMessage am) ? am.getText() : "";
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append(text.length() > 80 ? text.substring(0, 80) : text).append('\n');
        }
        String q = sb.toString().trim();
        return q.isBlank() ? userMessage : q;
    }
}
