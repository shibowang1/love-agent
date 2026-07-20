package com.yupi.yuaiagent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;

import java.util.Map;

/**
 * Raw HTTP example retained for comparing model invocation approaches.
 */
public final class HttpAiInvoke {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    private HttpAiInvoke() {
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("AI_DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_DASHSCOPE_API_KEY is not configured");
        }

        JSONObject systemMessage = new JSONObject(Map.of(
                "role", "system",
                "content", "You are a helpful assistant."));
        JSONObject userMessage = new JSONObject(Map.of(
                "role", "user",
                "content", "你是谁？"));
        JSONObject requestBody = new JSONObject()
                .set("model", "qwen-plus")
                .set("input", new JSONObject().set("messages", new JSONObject[]{systemMessage, userMessage}))
                .set("parameters", new JSONObject().set("result_format", "message"));

        try (HttpResponse response = HttpRequest.post(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(10_000)
                .execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException("DashScope request failed with HTTP " + response.getStatus());
            }
            System.out.println(response.body());
        }
    }
}
