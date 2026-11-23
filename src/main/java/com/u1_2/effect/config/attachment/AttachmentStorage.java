package com.u1_2.effect.config.attachment;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentStorage {

    private final AttachmentStore store;

    public String save(String rmaId, Resource source, long size) throws IOException {
        String key = rmaId + "/" + UUID.randomUUID();
        // DB가 롤백되면 로컬과 S3 모두에서 이번에 쓰려고 한 파일을 정리함
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteAfterRollback(key);
                }
            }
        });
        store.write(key, source, size);
        return key;
    }

    public Optional<Resource> find(String key) {
        return store.find(key);
    }

    private void deleteAfterRollback(String key) {
        try {
            store.delete(key);
        } catch (IOException | RuntimeException e) {
            log.warn("롤백된 첨부 파일을 삭제하지 못함. key={}", key, e);
        }
    }
}
