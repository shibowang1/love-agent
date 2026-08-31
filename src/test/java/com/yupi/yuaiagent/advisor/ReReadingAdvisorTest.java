package com.yupi.yuaiagent.advisor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReReadingAdvisorTest {

    @Test
    void repeatsTheActualQuestionWithoutLeavingTemplateVariables() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(response);
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt("我应该怎样表达自己的感受？"))
                .context(Map.of())
                .build();

        new ReReadingAdvisor().adviseCall(request, chain);

        ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(captor.capture());
        assertEquals("我应该怎样表达自己的感受？\n\nRead the question again: 我应该怎样表达自己的感受？",
                captor.getValue().prompt().getUserMessage().getText());
    }
}
