package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOperationToolTest {

    Path tempDir;

    @BeforeEach
    void createTestDirectory() throws Exception {
        tempDir = Path.of("target", "test-data", "file-tool-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(tempDir);
    }

    @AfterEach
    void removeTestDirectory() throws Exception {
        FileSystemUtils.deleteRecursively(tempDir);
    }

    @Test
    void writesAndReadsUtf8Files() {
        FileOperationTool tool = new FileOperationTool(tempDir);

        assertTrue(tool.writeFile("notes/date.txt", "上海周末约会计划").startsWith("File written successfully"));
        assertEquals("上海周末约会计划", tool.readFile("notes/date.txt"));
    }

    @Test
    void rejectsPathTraversal() {
        FileOperationTool tool = new FileOperationTool(tempDir.resolve("workspace"));

        assertEquals("Error writing file: invalid path", tool.writeFile("../outside.txt", "secret"));
        assertEquals("Error reading file: invalid path", tool.readFile("../outside.txt"));
    }

    @Test
    void rejectsOversizedContentAndFiles() throws Exception {
        FileOperationTool tool = new FileOperationTool(tempDir);
        String oversized = "a".repeat((int) FileOperationTool.MAX_FILE_SIZE_BYTES + 1);

        assertTrue(tool.writeFile("large.txt", oversized).contains("exceeds 1 MiB"));
        Files.write(tempDir.resolve("existing-large.txt"), new byte[(int) FileOperationTool.MAX_FILE_SIZE_BYTES + 1]);
        assertTrue(tool.readFile("existing-large.txt").contains("exceeds 1 MiB"));
    }

    @Test
    void reportsMissingFile() {
        FileOperationTool tool = new FileOperationTool(tempDir);

        assertEquals("Error reading file: file does not exist", tool.readFile("missing.txt"));
    }
}
