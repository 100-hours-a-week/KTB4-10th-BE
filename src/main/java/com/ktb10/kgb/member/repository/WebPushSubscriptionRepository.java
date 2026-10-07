package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Web Push endpoint 중복 확인과 회원·세션별 구독 폐기를 담당합니다. */
public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    Optional<WebPushSubscription> findByEndpointHash(byte[] endpointHash);

    Optional<WebPushSubscription> findByIdAndMemberId(Long id, Long memberId);

    long countByMemberIdAndStatus(Long memberId, WebPushSubscriptionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WebPushSubscription subscription "
            + "set subscription.status = :revokedStatus, "
            + "subscription.revokedAt = :revokedAt, subscription.updatedAt = :revokedAt "
            + "where subscription.authSession.id = :sessionId "
            + "and subscription.status = :activeStatus")
    int revokeAllActiveByAuthSessionId(
            @Param("sessionId") Long sessionId,
            @Param("activeStatus") WebPushSubscriptionStatus activeStatus,
            @Param("revokedStatus") WebPushSubscriptionStatus revokedStatus,
            @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WebPushSubscription subscription "
            + "set subscription.status = :revokedStatus, "
            + "subscription.revokedAt = :revokedAt, subscription.updatedAt = :revokedAt "
            + "where subscription.member.id = :memberId "
            + "and subscription.status = :activeStatus")
    int revokeAllActiveByMemberId(
            @Param("memberId") Long memberId,
            @Param("activeStatus") WebPushSubscriptionStatus activeStatus,
            @Param("revokedStatus") WebPushSubscriptionStatus revokedStatus,
            @Param("revokedAt") LocalDateTime revokedAt);
}
