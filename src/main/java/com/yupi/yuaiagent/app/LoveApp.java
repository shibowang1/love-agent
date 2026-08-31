package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Core application service for relationship counselling conversations.
 */
@Component
public class LoveApp {

    private static final String SYSTEM_PROMPT = """
            你是一名深耕恋爱心理领域的情感咨询助手。首次交流时说明身份，并告诉用户可以倾诉情感问题。
            你需要围绕单身、恋爱、已婚三种状态进行有针对性的沟通：单身状态关注社交圈拓展和追求心仪对象；
            恋爱状态关注沟通与习惯差异造成的矛盾；已婚状态关注家庭责任与亲属关系。
            引导用户说明事情经过、对方反应和自身想法，再给出具体、尊重且可执行的建议。
            遇到暴力、自伤或其他高风险情况时，优先建议用户寻求当地紧急服务或专业人员帮助。
            """;

    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor ragAdvisor;
    private final ToolCallback[] allTools;

    public LoveApp(ChatModel dashscopeChatModel,
                   ChatMemory chatMemory,
                   RetrievalAugmentationAdvisor ragAdvisor,
                   @Qualifier("allTools") ToolCallback[] allTools) {
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.ragAdvisor = ragAdvisor;
        this.allTools = allTools;
    }

    public String doChat(String message, String chatId) {
        validateInput(message, chatId);
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    public LoveReport doChatWithReport(String message, String chatId) {
        validateInput(message, chatId);
        return chatClient.prompt()
                .system(SYSTEM_PROMPT + "\n每次对话后生成一份简洁的恋爱建议报告，包括标题和建议列表。")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
    }

    public String doChatWithRag(String message, String chatId) {
        validateInput(message, chatId);
        return chatClient.prompt()
                .user(message)
                .advisors(ragAdvisor, new MyLoggerAdvisor())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    public String doChatWithTools(String message, String chatId) {
        validateInput(message, chatId);
        return chatClient.prompt()
                .user(message)
                .advisors(new MyLoggerAdvisor())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(allTools)
                .call()
                .content();
    }

    private void validateInput(String message, String chatId) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (!StringUtils.hasText(chatId)) {
            throw new IllegalArgumentException("chatId must not be blank");
        }
    }

    public record LoveReport(String title, List<String> suggestions) {
    }
}
