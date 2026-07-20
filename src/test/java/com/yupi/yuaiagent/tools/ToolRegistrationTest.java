package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolRegistrationTest {

    @Test
    void exposesFileAndSearchToolCallbacks() {
        Set<String> names = Arrays.stream(new ToolRegistration("test-key").allTools())
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(Set.of("readFile", "writeFile", "searchWeb"), names);
    }
}
