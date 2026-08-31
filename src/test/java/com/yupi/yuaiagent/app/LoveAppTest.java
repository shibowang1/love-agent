package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.tools.ToolRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoveAppTest {

    private ChatModel chatModel;
    private RetrievalAugmentationAdvisor ragAdvisor;
    private LoveApp loveApp;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            boolean reportRequested = prompt.getSystemMessage().getText().contains("恋爱建议报告");
            String text = reportRequested
                    ? "{\"title\":\"沟通建议\",\"suggestions\":[\"先倾听对方\"]}"
                    : "mock answer";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        });

        ragAdvisor = mock(RetrievalAugmentationAdvisor.class);
        when(ragAdvisor.getName()).thenReturn("MockRagAdvisor");
        when(ragAdvisor.adviseCall(any(ChatClientRequest.class), any(CallAdvisorChain.class)))
                .thenAnswer(invocation -> invocation.<CallAdvisorChain>getArgument(1)
                        .nextCall(invocation.getArgument(0)));

        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();
        loveApp = new LoveApp(chatModel, memory, ragAdvisor, new ToolRegistration("").allTools());
    }

    @Test
    void normalChatUsesConversationMemory() {
        assertEquals("mock answer", loveApp.doChat("我最近有些焦虑", "chat-1"));
        assertEquals("mock answer", loveApp.doChat("我刚才说了什么？", "chat-1"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(promptCaptor.capture());
        Prompt secondPrompt = promptCaptor.getAllValues().getLast();
        assertTrue(secondPrompt.getInstructions().stream()
                .anyMatch(message -> "我最近有些焦虑".equals(message.getText())));
    }

    @Test
    void createsStructuredReport() {
        LoveApp.LoveReport report = loveApp.doChatWithReport("我们总因沟通争吵", "report-1");

        assertNotNull(report);
        assertEquals("沟通建议", report.title());
        assertEquals(List.of("先倾听对方"), report.suggestions());
    }

    @Test
    void appliesRagAdvisor() {
        assertEquals("mock answer", loveApp.doChatWithRag("单身时如何扩大社交圈？", "rag-1"));

        verify(ragAdvisor).adviseCall(any(ChatClientRequest.class), any(CallAdvisorChain.class));
    }

    @Test
    void registersToolCallbacksForToolChat() {
        assertEquals("mock answer", loveApp.doChatWithTools("请搜索上海约会地点", "tool-1"));
        verify(chatModel).call(any(Prompt.class));
    }
}
