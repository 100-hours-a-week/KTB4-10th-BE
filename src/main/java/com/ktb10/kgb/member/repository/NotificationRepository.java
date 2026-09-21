package com.ktb10.kgb.member.repository;

import com.ktb10.kgb.member.entity.Notification;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 회원 소유 범위 안에서 미읽음 알림을 조회하고 삭제하는 Repository입니다. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByRecipientIdOrderByCreatedAtDescIdDesc(
            Long recipientMemberId,
            Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientMemberId);

    long deleteByIdAndRecipientId(Long id, Long recipientMemberId);

    long countByRecipientId(Long recipientMemberId);

    @Query("select notification.id from Notification notification "
            + "where notification.recipient.id = :recipientMemberId "
            + "order by notification.createdAt asc, notification.id asc")
    List<Long> findOldestIdsByRecipientMemberId(
            @Param("recipientMemberId") Long recipientMemberId,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification notification "
            + "where notification.recipient.id = :recipientMemberId "
            + "and notification.id in :notificationIds")
    int deleteAllByRecipientMemberIdAndIdIn(
            @Param("recipientMemberId") Long recipientMemberId,
            @Param("notificationIds") Collection<Long> notificationIds);
}
