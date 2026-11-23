package com.u1_2.effect.config.attachment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 로컬 첨부를 내부 키로 저장함. 사용자 파일명은 DB에 보관하고 디스크 경로에는 쓰지 않음.
 */
@Component
@Profile({"local", "test"})
public class LocalAttachmentStorage implements AttachmentStore {

    private final Path root;

    public LocalAttachmentStorage(@Value("${app.attachments.directory:./storage/attachments}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void write(String key, Resource source, long size) throws IOException {
        Path destination = root.resolve(key);
        Files.createDirectories(destination.getParent());
        try (var input = source.getInputStream()) {
            Files.copy(input, destination);
        }
    }

    @Override
    public Optional<Resource> find(String key) {
        try {
            Path relative = Path.of(key);
            Path file = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !file.startsWith(root)) {
                return Optional.empty();
            }
            // 심볼릭 링크까지 따라간 실제 경로가 보관 폴더 안에 있을 때만 내려줌
            Path realRoot = root.toRealPath();
            Path realFile = file.toRealPath();
            return realFile.startsWith(realRoot) && Files.isRegularFile(realFile)
                    ? Optional.of(new FileSystemResource(realFile)) : Optional.empty();
        } catch (NoSuchFileException | InvalidPathException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("첨부 파일을 읽지 못했습니다.", e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(root.resolve(key));
    }
}
