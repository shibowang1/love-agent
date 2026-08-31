package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
class LoveAppLiveSmokeTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void exercisesChatReportRagAndToolsAgainstLocalServices() {
        String chatId = UUID.randomUUID().toString();
        String chatResponse = loveApp.doChat("你好，请简短介绍你能提供的帮助。", chatId);
        assertTextResponse(chatResponse);
        printTextResponse("普通聊天", chatResponse);

        LoveApp.LoveReport report = loveApp.doChatWithReport(
                "我想改善和伴侣的沟通。", chatId + "-report");
        assertNotNull(report);
        assertFalse(report.title() == null || report.title().isBlank());
        assertNotNull(report.suggestions());
        assertFalse(report.suggestions().isEmpty());
        printReport(report);

        String ragResponse = loveApp.doChatWithRag(
                "单身时如何缓解恋爱焦虑？", chatId + "-rag");
        assertTextResponse(ragResponse);
        printTextResponse("RAG 聊天", ragResponse);

        String toolResponse = loveApp.doChatWithTools(
                "请搜索两个上海约会地点。", chatId + "-tool");
        assertTextResponse(toolResponse);
        printTextResponse("Tool Calling", toolResponse);
    }

    private void assertTextResponse(String response) {
        assertNotNull(response);
        assertFalse(response.isBlank());
    }

    private void printTextResponse(String scene, String response) {
        printHeader(scene);
        System.out.println(response);
    }

    private void printReport(LoveApp.LoveReport report) {
        printHeader("结构化报告");
        System.out.println("标题：" + report.title());
        System.out.println("建议：");
        for (int index = 0; index < report.suggestions().size(); index++) {
            System.out.printf("%d. %s%n", index + 1, report.suggestions().get(index));
        }
    }

    private void printHeader(String scene) {
        System.out.println();
        System.out.println("==================== " + scene + " ====================");
    }
}
