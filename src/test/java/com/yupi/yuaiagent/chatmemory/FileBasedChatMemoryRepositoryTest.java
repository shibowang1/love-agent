package com.yupi.yuaiagent.chatmemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBasedChatMemoryRepositoryTest {

    Path tempDir;

    @BeforeEach
    void createTestDirectory() throws Exception {
        tempDir = Path.of("target", "test-data", "chat-memory-" + UUID.randomUUID()).toAbsolutePath();
        java.nio.file.Files.createDirectories(tempDir);
    }

    @AfterEach
    void removeTestDirectory() throws Exception {
        FileSystemUtils.deleteRecursively(tempDir);
    }

    @Test
    void persistsMessagesAcrossRepositoryInstances() {
        FileBasedChatMemoryRepository first = new FileBasedChatMemoryRepository(tempDir);
        first.saveAll("conversation-1", List.of(new UserMessage("你好"), new AssistantMessage("你好")));

        FileBasedChatMemoryRepository reopened = new FileBasedChatMemoryRepository(tempDir);
        assertEquals(List.of("conversation-1"), reopened.findConversationIds());
        assertEquals(List.of("你好", "你好"), reopened.findByConversationId("conversation-1")
                .stream().map(Message::getText).toList());
    }

    @Test
    void messageWindowTruncatesOldMessagesAndPersistsTheWindow() {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(tempDir);
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(3)
                .build();

        memory.add("window", List.of(new UserMessage("1"), new AssistantMessage("2")));
        memory.add("window", List.of(new UserMessage("3"), new AssistantMessage("4")));

        assertEquals(List.of("2", "3", "4"), memory.get("window").stream().map(Message::getText).toList());
        assertEquals(3, repository.findByConversationId("window").size());
    }

    @Test
    void rejectsIllegalConversationIdsAndClearsMemory() {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(tempDir);
        assertThrows(IllegalArgumentException.class,
                () -> repository.saveAll("../escape", List.of(new UserMessage("x"))));

        repository.saveAll("safe_id", List.of(new UserMessage("x")));
        repository.deleteByConversationId("safe_id");
        assertTrue(repository.findByConversationId("safe_id").isEmpty());
    }

    @Test
    void isolatesConcurrentConversationWrites() throws Exception {
        FileBasedChatMemoryRepository repository = new FileBasedChatMemoryRepository(tempDir);
        int conversations = 8;
        ExecutorService executor = Executors.newFixedThreadPool(conversations);
        CountDownLatch ready = new CountDownLatch(conversations);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();
        try {
            for (int index = 0; index < conversations; index++) {
                int id = index;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        repository.saveAll("chat-" + id, List.of(new UserMessage("message-" + id)));
                    }
                    catch (Throwable failure) {
                        synchronized (failures) {
                            failures.add(failure);
                        }
                    }
                });
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
        }
        finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertTrue(failures.isEmpty());
        assertEquals(conversations, repository.findConversationIds().size());
        for (int index = 0; index < conversations; index++) {
            assertEquals("message-" + index, repository.findByConversationId("chat-" + index).getFirst().getText());
        }
    }
}
