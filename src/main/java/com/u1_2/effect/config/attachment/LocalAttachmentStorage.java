package com.u1_2.effect.config.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 로컬 첨부를 내부 키로 저장함. 사용자 파일명은 DB에 보관하고 디스크 경로에는 쓰지 않음.
 */
@Slf4j
@Component
@Profile({"local", "test"})
public class LocalAttachmentStorage {

    private final Path root;

    public LocalAttachmentStorage(@Value("${app.attachments.directory:./storage/attachments}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String save(String rmaId, InputStream source) throws IOException {
        Path key = Path.of(rmaId, UUID.randomUUID().toString());
        Path destination = root.resolve(key);
        // DB 저장이 취소되면 먼저 저장한 로컬 파일도 삭제함
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteRolledBackFile(destination);
                }
            }
        });
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
        return key.toString();
    }

    public Optional<FileSystemResource> find(String key) {
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

    private void deleteRolledBackFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("롤백된 첨부 파일을 삭제하지 못했습니다.", e);
        }
    }
}
