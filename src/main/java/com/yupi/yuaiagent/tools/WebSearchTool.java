package com.yupi.yuaiagent.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class WebSearchTool {

    private static final URI SEARCH_API_URI = URI.create("https://www.searchapi.io/api/v1/search");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String apiKey;
    private final HttpClient httpClient;
    private final URI searchApiUri;
    private final Duration requestTimeout;

    public WebSearchTool(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                SEARCH_API_URI, REQUEST_TIMEOUT);
    }

    WebSearchTool(String apiKey, HttpClient httpClient, URI searchApiUri, Duration requestTimeout) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.searchApiUri = searchApiUri;
        this.requestTimeout = requestTimeout;
    }

    @Tool(description = "Search Baidu and return at most five concise web results")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query) {
        if (query == null || query.isBlank()) {
            return "Error searching Baidu: query must not be blank";
        }
        if (apiKey == null || apiKey.isBlank()) {
            return "Error searching Baidu: API key is not configured";
        }
        try {
            String separator = searchApiUri.toString().contains("?") ? "&" : "?";
            URI requestUri = URI.create(searchApiUri + separator
                    + "engine=baidu&q=" + encode(query) + "&api_key=" + encode(apiKey));
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "Error searching Baidu: HTTP status " + response.statusCode();
            }
            return selectResults(response.body());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error searching Baidu: request interrupted";
        }
        catch (Exception e) {
            return "Error searching Baidu: request failed";
        }
    }

    private String selectResults(String responseBody) {
        JSONObject response = JSONUtil.parseObj(responseBody);
        JSONArray organicResults = response.getJSONArray("organic_results");
        JSONArray selected = new JSONArray();
        if (organicResults == null) {
            return selected.toString();
        }
        int limit = Math.min(5, organicResults.size());
        for (int index = 0; index < limit; index++) {
            JSONObject source = organicResults.getJSONObject(index);
            JSONObject item = new JSONObject();
            item.set("title", source.getStr("title", ""));
            item.set("link", source.getStr("link", ""));
            item.set("snippet", source.getStr("snippet", ""));
            selected.add(item);
        }
        return selected.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
