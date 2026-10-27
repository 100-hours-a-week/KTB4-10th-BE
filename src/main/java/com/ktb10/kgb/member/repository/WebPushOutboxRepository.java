package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.WebPushOutbox;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
