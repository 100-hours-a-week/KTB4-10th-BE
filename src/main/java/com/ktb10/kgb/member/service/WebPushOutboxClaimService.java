package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.entity.WebPushOutbox;
import com.ktb10.kgb.member.repository.WebPushOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 다중 Worker가 같은 작업을 동시에 발송하지 않도록 짧은 DB 트랜잭션으로 선점합니다. */
@Service
public class WebPushOutboxClaimService {

    private final WebPushOutboxRepository repository;
    private final Clock clock;

    public WebPushOutboxClaimService(WebPushOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<WebPushOutboxTask> claim(int batchSize, Duration lease) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = repository.findClaimableIds(now, now.minus(lease), batchSize);
        return repository.findAllById(ids).stream()
                .map(task -> claim(task, now))
                .toList();
    }

    private WebPushOutboxTask claim(WebPushOutbox task, LocalDateTime now) {
        String token = UUID.randomUUID().toString();
        task.claim(token, now);
        return new WebPushOutboxTask(
                task.getId(),
                task.getNotificationId(),
                task.getRecipientMemberId(),
                task.getSubscriptionId(),
                task.getPayloadJson(),
                task.getAttemptCount(),
                token);
    }
}
