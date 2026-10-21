package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.NotificationOccurrence;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 반복 실행 가능한 알림의 발생 건을 DB 유일 제약으로 선점합니다. */
public interface NotificationOccurrenceRepository
        extends JpaRepository<NotificationOccurrence, Long> {

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT IGNORE INTO notification_occurrences (
                        recipient_member_id,
                        type,
                        reference_type,
                        reference_id,
                        occurrence_key,
                        created_at
                    ) VALUES (
                        :recipientMemberId,
                        :type,
                        :referenceType,
                        :referenceId,
                        :occurrenceKey,
                        :createdAt
                    )
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("recipientMemberId") Long recipientMemberId,
            @Param("type") String type,
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId,
            @Param("occurrenceKey") String occurrenceKey,
            @Param("createdAt") LocalDateTime createdAt);
}
