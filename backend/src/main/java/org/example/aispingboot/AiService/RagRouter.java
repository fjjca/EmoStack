package org.example.aispingboot.AiService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class RagRouter {

    private static final String ROUTER_SYSTEM_PROMPT =
            "你是对话路由判断器。判断用户的最新消息是否属于以下类型（需要检索心理知识库来回答）：\n" +
                    "1. 询问心理知识、心理学术语的含义或科普（如：什么是情绪内耗、焦虑症有哪些症状）\n" +
                    "2. 询问症状、病因、治疗、缓解方法（如：失眠怎么办、如何缓解压力）\n" +
                    "3. 询问具体的应对技巧和方法（如：怎么温和地拒绝别人、如何学会自我接纳）\n" +
                    "以下情况不需要检索：情绪倾诉、日常分享、闲聊寒暄、单纯的情绪表达（如：我今天好累、我好难过），以及涉及自伤、轻生等安全话题。\n" +
                    "只输出一个 JSON：{\"needRag\": true} 或 {\"needRag\": false}，不要输出任何其他内容。";

    @Autowired
    private OpenAiChatModel openAiChatModel;

    // 逗号分隔关键词（YAML 列表形式无法用 @Value 注入，故配置为字符串）
    @Value("${rag.router-keywords:}")
    private String routerKeywordsCsv;

    @Value("${rag.router-model-enabled:true}")
    private boolean modelEnabled;

    public boolean needsRag(String userMessage, List<Message> recentMessages) {
        String text = userMessage == null ? "" : userMessage;
        if (matchKeywords(text)) {
            return true;
        }
        // 指代型提问（如"怎么缓解这种感觉"）：上文提到过主题关键词则视为延续该话题，需要检索
        if (isReferentialQuery(text) && contextContainsKeyword(recentMessages)) {
            return true;
        }
        if (modelEnabled) {
            return modelDecide(text, recentMessages);
        }
        return false;
    }

    // 指代型提问：短句 + 指代/追问词，脱离上下文无法定位主题
    private static final String[] REFERENTIAL_MARKS = {"这个", "这种", "这些", "那个", "它", "那", "感觉", "怎么办", "怎么", "为什么", "如何", "方法", "问题", "情况", "症状", "事情"};
    private static final int REFERENTIAL_MAX_LEN = 24;

    private boolean isReferentialQuery(String text) {
        if (text == null || text.isBlank() || text.length() > REFERENTIAL_MAX_LEN) {
            return false;
        }
        for (String mark : REFERENTIAL_MARKS) {
            if (text.contains(mark)) {
                return true;
            }
        }
        return false;
    }

    private List<String> keywordList() {
        if (routerKeywordsCsv == null || routerKeywordsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(routerKeywordsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private boolean contextContainsKeyword(List<Message> recentMessages) {
        List<String> keywords = keywordList();
        if (keywords.isEmpty() || recentMessages == null) {
            return false;
        }
        for (Message m : recentMessages) {
            String text = m.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            for (String kw : keywords) {
                if (text.contains(kw)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchKeywords(String text) {
        List<String> keywords = keywordList();
        if (keywords.isEmpty() || text.isBlank()) {
            return false;
        }
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean modelDecide(String userMessage, List<Message> recentMessages) {
        try {
            StringBuilder ctx = new StringBuilder();
            if (recentMessages != null) {
                for (Message m : recentMessages) {
                    String role = m instanceof UserMessage ? "用户" : "AI";
                    ctx.append(role).append("：").append(m.getText()).append("\n");
                }
            }
            ctx.append("用户：").append(userMessage).append("\n");
            ChatClient client = ChatClient.builder(openAiChatModel)
                    .defaultOptions(OpenAiChatOptions.builder().temperature(0.1).build())
                    .build();
            String resp = client.prompt()
                    .system(ROUTER_SYSTEM_PROMPT)
                    .user(ctx.toString())
                    .call()
                    .content();
            return resp != null && resp.contains("\"needRag\": true");
        } catch (Exception e) {
            log.warn("RAG 路由模型判断失败，降级为不检索: {}", e.getMessage());
            return false;
        }
    }
}
