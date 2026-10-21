package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.member.dto.response.NotificationItemResponse;
import com.ktb10.kgb.member.dto.response.NotificationListResponse;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.event.NotificationCreatedEvent;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationOccurrenceRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 인앱 알림 생성, 조회와 삭제를 담당합니다. */
@Service
public class NotificationService {

    private static final int MAX_NOTIFICATIONS_PER_MEMBER = 20;

    private final NotificationRepository notificationRepository;
    private final NotificationOccurrenceRepository notificationOccurrenceRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationOccurrenceRepository notificationOccurrenceRepository,
            MemberRepository memberRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.notificationOccurrenceRepository = notificationOccurrenceRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(
            Long recipientMemberId,
            NotificationType type,
            String title,
            String body,
            NotificationReferenceType referenceType,
            String referenceId) {
        createNotification(
                recipientMemberId,
                type,
                title,
                body,
                referenceType,
                referenceId,
                LocalDateTime.now(clock));
    }

    /** 같은 발생 건은 인스턴스 수와 재실행 횟수에 관계없이 한 번만 저장합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createOnce(
            Long recipientMemberId,
            NotificationType type,
            String title,
            String body,
            NotificationReferenceType referenceType,
            String referenceId,
            String occurrenceKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        int claimed = notificationOccurrenceRepository.insertIfAbsent(
                recipientMemberId,
                type.name(),
                referenceType.name(),
                referenceId,
                occurrenceKey,
                now);
        if (claimed == 0) {
            return false;
        }
        createNotification(
                recipientMemberId,
                type,
                title,
                body,
                referenceType,
                referenceId,
                now);
        return true;
    }

    private void createNotification(
            Long recipientMemberId,
            NotificationType type,
            String title,
            String body,
            NotificationReferenceType referenceType,
            String referenceId,
            LocalDateTime now) {
        Member recipient = memberRepository.findActiveByIdForUpdate(recipientMemberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        Notification notification = notificationRepository.saveAndFlush(Notification.create(
                recipient,
                type,
                title,
                body,
                referenceType,
                referenceId,
                now));
        trimOldNotifications(recipientMemberId);
        eventPublisher.publishEvent(new NotificationCreatedEvent(
                recipientMemberId,
                NotificationItemResponse.from(notification),
                recipient.isPushEnabled()));
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
