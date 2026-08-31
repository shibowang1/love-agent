package com.yupi.yuaiagent.chatmemory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

@Configuration
public class ChatMemoryConfig {

    @Bean
    ChatMemoryRepository chatMemoryRepository(Environment environment) {
        String defaultDir = Path.of(System.getProperty("user.dir"), "chat-memory").toString();
        String configuredDir = environment.getProperty("app.chat-memory.dir", defaultDir);
        return new FileBasedChatMemoryRepository(Path.of(configuredDir));
    }

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }
}
