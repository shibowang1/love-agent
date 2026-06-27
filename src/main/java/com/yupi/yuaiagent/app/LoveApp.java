package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;

    private final ChatMemory chatMemory;

    private final QueryTransformer queryTransformer;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    public LoveApp(ChatModel dashscopeChatModel) {
        // 初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        this.chatMemory = new FileBasedChatMemory(fileDir);
//        //基于内存的记忆，默认的
//        ChatMemory chatMemory1 = new InMemoryChatMemory();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(this.chatMemory)
//                        new MyLoggerAdvisor(),
//                        new ReReadingAdvisor()
                )
                .build();

        // 初始化查询改写器，用于在 RAG 检索前将用户口语化查询改写为更精准的检索查询
        ChatClient.Builder rewriterBuilder = ChatClient.builder(dashscopeChatModel);
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(rewriterBuilder)
                .targetSearchSystem("恋爱心理学知识库")
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    record LoveReport(String title, List<String> suggestions) {
    }

    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    @Resource
    private VectorStore loveAppVectorStore; //基于内存的向量存储

    @Resource
    private VectorStore pgVectorVectorStore; //基于阿里云pgvector的向量存储

    public String doChatWithRag(String message, String chatId) {
        // 1. 查询改写：结合对话历史，将用户口语化/省略查询改写为完整独立的检索查询
        List<Message> history = chatMemory.get(chatId, 10);
        Query originalQuery = Query.builder()
                .text(message)
                .history(history)
                .build();
        Query rewrittenQuery = queryTransformer.transform(originalQuery);
        String searchQuery = rewrittenQuery.text();
        log.info("查询改写 - 原始: [{}] → 改写: [{}]", message, searchQuery);

        // 2. 用改写后的查询从向量库检索相关文档
        List<Document> docs = pgVectorVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(searchQuery)
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build());
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        // 3. 构建 RAG prompt（手动替换上下文，绕过 Spring AI 的 PromptTemplate 兼容问题）
        String ragUserText = message + "\n\n"
                + "Context information is below, surrounded by ---------------------\n"
                + "---------------------\n"
                + context + "\n"
                + "---------------------\n"
                + "Given the context and provided history information and not prior knowledge, "
                + "reply to the user comment. If the answer is not in the context, inform "
                + "the user that you can't answer the question.";

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(ragUserText)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }



}
