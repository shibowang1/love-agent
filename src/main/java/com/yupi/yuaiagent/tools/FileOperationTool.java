package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public class FileOperationTool {

    static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

    private final Path baseDirectory;

    public FileOperationTool() {
        this(Path.of(FileConstant.FILE_SAVE_DIR, "file"));
    }

    public FileOperationTool(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Tool(description = "Read a UTF-8 text file from the agent workspace")
    public String readFile(@ToolParam(description = "Relative path of the file to read") String fileName) {
        try {
            Path target = resolveInsideBase(fileName);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                return "Error reading file: file does not exist";
            }
            ensureExistingPathInsideBase(target);
            long size = Files.size(target);
            if (size > MAX_FILE_SIZE_BYTES) {
                return "Error reading file: file exceeds 1 MiB limit";
            }
            return Files.readString(target, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException e) {
            return "Error reading file: invalid path";
        }
        catch (IOException e) {
            return "Error reading file: I/O failure";
        }
    }

    @Tool(description = "Write a UTF-8 text file to the agent workspace")
    public String writeFile(
            @ToolParam(description = "Relative path of the file to write") String fileName,
            @ToolParam(description = "Content to write to the file") String content) {
        if (content == null) {
            return "Error writing file: content must not be null";
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_SIZE_BYTES) {
            return "Error writing file: content exceeds 1 MiB limit";
        }
        try {
            Path target = resolveInsideBase(fileName);
            Path parent = target.getParent();
            Files.createDirectories(parent);
            ensureExistingPathInsideBase(parent);
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return "File written successfully: " + baseDirectory.relativize(target);
        }
        catch (IllegalArgumentException e) {
            return "Error writing file: invalid path";
        }
        catch (IOException e) {
            return "Error writing file: I/O failure";
        }
    }

    private Path resolveInsideBase(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("file name is blank");
        }
        Path relative = Path.of(fileName);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("absolute paths are forbidden");
        }
        Path resolved = baseDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(baseDirectory) || resolved.equals(baseDirectory)) {
            throw new IllegalArgumentException("path escapes base directory");
        }
        return resolved;
    }

    private void ensureExistingPathInsideBase(Path path) throws IOException {
        Path realBase = Files.createDirectories(baseDirectory).toRealPath();
        if (!path.toRealPath().startsWith(realBase)) {
            throw new IllegalArgumentException("symbolic link escapes base directory");
        }
    }
}
