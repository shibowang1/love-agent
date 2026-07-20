package com.yupi.yuaiagent.demo.invoke;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Minimal Spring AI invocation example. It is intentionally not a Spring bean,
 * so application startup never triggers an unexpected model call.
 */
public final class SpringAiAiInvoke {

    private SpringAiAiInvoke() {
    }

    public static String call(ChatModel chatModel, String message) {
        return chatModel.call(message);
    }
}
