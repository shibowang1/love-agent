package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    private final String searchApiKey;

    public ToolRegistration(@Value("${search-api.api-key:}") String searchApiKey) {
        this.searchApiKey = searchApiKey;
    }

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        return ToolCallbacks.from(
            fileOperationTool,
            webSearchTool
        );
    }
}
