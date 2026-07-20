package com.yupi.yuaiagent.advisor;

import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Re2 (re-reading) advisor: repeats the original question before model invocation.
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(before(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(before(request));
    }

    private ChatClientRequest before(ChatClientRequest request) {
        String originalQuestion = request.prompt().getUserMessage().getText();
        Prompt prompt = request.prompt().augmentUserMessage(
                originalQuestion + "\n\nRead the question again: " + originalQuestion);
        return request.mutate().prompt(prompt).build();
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }
}
