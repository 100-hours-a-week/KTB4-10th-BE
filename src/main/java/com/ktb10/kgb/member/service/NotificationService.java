package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.member.dto.response.NotificationListResponse;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인앱 알림 생성, 조회와 삭제를 담당합니다. */
@Service
public class NotificationService {

    private static final int MAX_NOTIFICATIONS_PER_MEMBER = 20;

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository,
            MemberRepository memberRepository,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long memberId, int page, int size) {
        Page<Notification> notifications = notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        memberId,
                        PageRequest.of(page - 1, size));
        return NotificationListResponse.from(notifications, page);
    }

    @Transactional
    public void deleteNotification(Long memberId, Long notificationId) {
        notificationRepository.deleteByIdAndRecipientId(notificationId, memberId);
    }

    @Transactional
    public void deleteAllNotifications(Long memberId) {
        List<Long> targetIds = notificationRepository.findOldestIdsByRecipientMemberId(
                memberId,
                Pageable.unpaged());
        if (!targetIds.isEmpty()) {
            notificationRepository.deleteAllByRecipientMemberIdAndIdIn(memberId, targetIds);
        }
    }

    @Transactional
    public void create(
            Long recipientMemberId,
            NotificationType type,
            String title,
            String body,
            NotificationReferenceType referenceType,
            String referenceId) {
        // TODO: #74 가이드북 완료·일정 임박 사건의 원본 트랜잭션 커밋 후 호출을 연결한다.
        Member recipient = memberRepository.findActiveByIdForUpdate(recipientMemberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        notificationRepository.saveAndFlush(Notification.create(
                recipient,
                type,
                title,
                body,
                referenceType,
                referenceId,
                LocalDateTime.now(clock)));
        trimOldNotifications(recipientMemberId);
    }

    private void trimOldNotifications(Long recipientMemberId) {
        long overflowCount = notificationRepository.countByRecipientId(recipientMemberId)
                - MAX_NOTIFICATIONS_PER_MEMBER;
        if (overflowCount <= 0) {
            return;
        }

        List<Long> oldestIds = notificationRepository.findOldestIdsByRecipientMemberId(
                recipientMemberId,
                PageRequest.of(0, Math.toIntExact(overflowCount)));
        notificationRepository.deleteAllByRecipientMemberIdAndIdIn(
                recipientMemberId,
                oldestIds);
    }
}
