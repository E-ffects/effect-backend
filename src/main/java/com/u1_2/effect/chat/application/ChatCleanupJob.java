package com.u1_2.effect.chat.application;

import com.u1_2.effect.chat.domain.ChatRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 마지막 메시지 후 보관 기간이 지난 대화를 지움. 삭제는 멱등이라 여러 인스턴스가 동시에 실행해도 됨 */
@Slf4j
@Component
public class ChatCleanupJob {

    private final ChatRepository repository;
    private final Clock appClock;
    private final int retentionDays;

    public ChatCleanupJob(ChatRepository repository, Clock appClock,
                          @Value("${app.chat.retention-days:30}") int retentionDays) {
        this.repository = repository;
        this.appClock = appClock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.chat.cleanup-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpired() {
        int deleted = repository.deleteThreadsBefore(OffsetDateTime.now(appClock).minusDays(retentionDays));
        if (deleted > 0) {
            log.info("보관 기간이 지난 채팅 {}건을 삭제함", deleted);
        }
    }
}
