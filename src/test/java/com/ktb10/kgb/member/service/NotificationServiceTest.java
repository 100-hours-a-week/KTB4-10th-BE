package com.ktb10.kgb.member.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MemberRepository memberRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                memberRepository,
                Clock.systemUTC());
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
}
