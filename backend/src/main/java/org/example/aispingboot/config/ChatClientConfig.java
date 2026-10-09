package org.example.aispingboot.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(30) // 保留最新30条消息
                .build();
    }

    @Bean("open-ai")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                .defaultSystem("你是一个专业的心理疏导师，温和耐心，善于倾听，能够提供专业的心理支持和建议").build();
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .embeddingsPath("/embeddings")
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model("qwen3.7-text-embedding-flash").build());
    }

    @Bean
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Value("${rag.collection}") String collection,
            @Value("${rag.chroma-url:http://localhost:8000}") String chromaUrl) {
        ChromaApi chromaApi = ChromaApi.builder().baseUrl(chromaUrl).build();
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collection)
                .tenantName("default_tenant")
                .databaseName("default_database")
                .initializeSchema(true)
                .build();
    }
}
