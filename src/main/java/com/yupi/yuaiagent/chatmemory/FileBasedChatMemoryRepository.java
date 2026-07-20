package com.yupi.yuaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * 基于 Kryo 文件的对话记忆仓库。
 */
@Slf4j
public class FileBasedChatMemoryRepository implements ChatMemoryRepository {

    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
    private static final String FILE_SUFFIX = ".kryo";

    private final Path baseDir;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public FileBasedChatMemoryRepository(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建对话记忆目录: " + this.baseDir, e);
        }
    }

    @Override
    public List<String> findConversationIds() {
        try (var paths = Files.list(baseDir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(FILE_SUFFIX))
                    .map(name -> name.substring(0, name.length() - FILE_SUFFIX.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("读取对话记忆目录失败", e);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Path file = conversationFile(conversationId);
        ReentrantReadWriteLock.ReadLock readLock = lockFor(conversationId).readLock();
        readLock.lock();
        try {
            if (Files.notExists(file)) {
                return List.of();
            }
            try (Input input = new Input(Files.newInputStream(file))) {
                return readMessages(input);
            } catch (IOException | RuntimeException e) {
                log.warn("读取对话记忆失败，conversationId={}", conversationId, e);
                return List.of();
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        Path target = conversationFile(conversationId);
        ReentrantReadWriteLock.WriteLock writeLock = lockFor(conversationId).writeLock();
        writeLock.lock();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(baseDir, conversationId + "-", ".tmp");
            try (Output output = new Output(Files.newOutputStream(tempFile))) {
                newKryo().writeObject(output, new ArrayList<>(messages));
            }
            moveAtomically(tempFile, target);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("保存对话记忆失败: " + conversationId, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.debug("清理对话记忆临时文件失败: {}", tempFile, e);
                }
            }
            writeLock.unlock();
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Path file = conversationFile(conversationId);
        ReentrantReadWriteLock.WriteLock writeLock = lockFor(conversationId).writeLock();
        writeLock.lock();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("删除对话记忆失败: " + conversationId, e);
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message> readMessages(Input input) {
        ArrayList<Message> messages = newKryo().readObject(input, ArrayList.class);
        return List.copyOf(messages);
    }

    private Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ReentrantReadWriteLock lockFor(String conversationId) {
        return locks.computeIfAbsent(conversationId, ignored -> new ReentrantReadWriteLock());
    }

    private Path conversationFile(String conversationId) {
        validateConversationId(conversationId);
        Path file = baseDir.resolve(conversationId + FILE_SUFFIX).normalize();
        if (!file.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法 conversationId");
        }
        return file;
    }

    private void validateConversationId(String conversationId) {
        if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
            throw new IllegalArgumentException("conversationId 仅允许字母、数字、下划线和连字符，长度不超过 128");
        }
    }
}
