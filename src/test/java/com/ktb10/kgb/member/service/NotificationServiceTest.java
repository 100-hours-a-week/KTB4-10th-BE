package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                memberRepository,
                Clock.systemUTC(),
                eventPublisher);
    }

    @Test
    void deletesOnlyNotificationIdsCapturedAtSnapshotStart() {
        Long memberId = 1L;
        List<Long> snapshotIds = List.of(10L, 11L);
        given(notificationRepository.findOldestIdsByRecipientMemberId(
                org.mockito.ArgumentMatchers.eq(memberId),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(snapshotIds);

        notificationService.deleteAllNotifications(memberId);

        verify(notificationRepository).deleteAllByRecipientMemberIdAndIdIn(
                memberId,
                snapshotIds);
    }

    @Test
    void publishesCreatedNotificationForAfterCommitDelivery() {
        Long memberId = 1L;
        Member member = org.mockito.Mockito.mock(Member.class);
        Notification notification = Notification.create(
                member,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 생성 완료",
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "101",
                java.time.LocalDateTime.now());
        ReflectionTestUtils.setField(notification, "id", 301L);
        given(memberRepository.findActiveByIdForUpdate(memberId))
                .willReturn(Optional.of(member));
        given(member.isPushEnabled()).willReturn(false);
        given(notificationRepository.saveAndFlush(any(Notification.class)))
                .willReturn(notification);

        notificationService.create(
                memberId,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 생성 완료",
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                "101");

        ArgumentCaptor<com.ktb10.kgb.member.event.NotificationCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        com.ktb10.kgb.member.event.NotificationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().realtimeDeliveryEnabled()).isFalse();
    }
}
