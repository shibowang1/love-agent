package com.yupi.yuaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * Logs request and response metadata without exposing conversation content.
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        logRequest(request);
        ChatClientResponse response = chain.nextCall(request);
        logResponse(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        logRequest(request);
        return new ChatClientMessageAggregator()
                .aggregateChatClientResponse(chain.nextStream(request), this::logResponse);
    }

    private void logRequest(ChatClientRequest request) {
        int messageCount = request.prompt().getInstructions().size();
        int userTextLength = request.prompt().getUserMessage().getText().length();
        log.debug("AI request: messages={}, userTextLength={}", messageCount, userTextLength);
    }

    private void logResponse(ChatClientResponse response) {
        String text = response.chatResponse() == null
                || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null
                ? null
                : response.chatResponse().getResult().getOutput().getText();
        log.debug("AI response: textLength={}", text == null ? 0 : text.length());
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
