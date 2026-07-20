package com.yupi.yuaiagent.tools;

import cn.hutool.json.JSONUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicLong delayMillis = new AtomicLong();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            try {
                Thread.sleep(delayMillis.get());
                byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(), response.length);
                exchange.getResponseBody().write(response);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsEmptyArrayWhenNoOrganicResultsExist() {
        responseBody.set("{\"organic_results\":[]}");

        assertEquals("[]", tool(Duration.ofSeconds(1)).searchWeb("上海约会"));
    }

    @Test
    void handlesFewerThanFiveResultsAndTrimsFields() {
        responseBody.set("""
                {"organic_results":[
                  {"title":"A","link":"https://a.example","snippet":"one","raw":"discard"},
                  {"title":"B","link":"https://b.example","snippet":"two"}
                ]}
                """);

        var results = JSONUtil.parseArray(tool(Duration.ofSeconds(1)).searchWeb("上海约会"));
        assertEquals(2, results.size());
        assertEquals(3, results.getJSONObject(0).size());
        assertEquals("A", results.getJSONObject(0).getStr("title"));
    }

    @Test
    void reportsHttpErrors() {
        status.set(503);

        assertEquals("Error searching Baidu: HTTP status 503", tool(Duration.ofSeconds(1)).searchWeb("上海约会"));
    }

    @Test
    void enforcesRequestTimeout() {
        delayMillis.set(300);

        assertTrue(tool(Duration.ofMillis(30)).searchWeb("上海约会").startsWith("Error searching Baidu"));
    }

    private WebSearchTool tool(Duration timeout) {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search");
        return new WebSearchTool("test-key", HttpClient.newHttpClient(), uri, timeout);
    }
}
