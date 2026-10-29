package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.WebPushOutbox;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Web Push 발송 작업의 멱등 저장과 후속 Worker 조회 기반을 제공합니다. */
public interface WebPushOutboxRepository extends JpaRepository<WebPushOutbox, Long> {

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO web_push_outbox (
                        notification_id, recipient_member_id, subscription_id,
                        payload_json, status,
                        attempt_count, next_attempt_at, created_at, updated_at
                    ) VALUES (
                        :notificationId, :recipientMemberId, :subscriptionId,
                        CAST(:payloadJson AS JSON), 'PENDING',
                        0, :now, :now, :now
                    )
                    ON DUPLICATE KEY UPDATE id = id
                    """,
            nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("notificationId") Long notificationId,
            @Param("recipientMemberId") Long recipientMemberId,
            @Param("subscriptionId") Long subscriptionId,
            @Param("payloadJson") String payloadJson,
            @Param("now") LocalDateTime now);

    long countByNotificationIdAndSubscriptionId(Long notificationId, Long subscriptionId);

    @Query(
            value =
                    """
                    SELECT id
                    FROM web_push_outbox
                    WHERE (
                        status IN ('PENDING', 'RETRY_WAIT')
                        AND next_attempt_at <= :now
                    ) OR (
                        status = 'PROCESSING'
                        AND processing_started_at <= :staleBefore
                    )
                    ORDER BY next_attempt_at, id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<Long> findClaimableIds(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WebPushOutbox> findByIdAndStatusAndClaimToken(
            Long id,
            com.ktb10.kgb.member.entity.WebPushOutboxStatus status,
            String claimToken);

    @Query("select count(task) from WebPushOutbox task where task.status in :statuses")
    long countByStatuses(
            @Param("statuses") List<com.ktb10.kgb.member.entity.WebPushOutboxStatus> statuses);

    @Query("select min(task.createdAt) from WebPushOutbox task where task.status in :statuses")
    Optional<LocalDateTime> findOldestCreatedAtByStatuses(
            @Param("statuses") List<com.ktb10.kgb.member.entity.WebPushOutboxStatus> statuses);
}
