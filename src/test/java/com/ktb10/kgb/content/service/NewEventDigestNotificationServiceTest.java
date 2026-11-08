package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb10.kgb.content.repository.NewEventQuery;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.service.NotificationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NewEventDigestNotificationServiceTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 2);

    @Mock
    private NewEventQuery newEventQuery;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationService notificationService;

    private NewEventDigestNotificationService service;

    @BeforeEach
    void setUp() {
        service = new NewEventDigestNotificationService(
                newEventQuery,
                memberRepository,
                notificationService);
    }

    @Test
    void skipsMemberLookupWhenThereAreNoNewEvents() {
        givenEventCount(0L);

        NewEventDigestNotificationSummary summary =
                service.createForReferenceDate(REFERENCE_DATE);

        assertThat(summary).isEqualTo(
                new NewEventDigestNotificationSummary(0L, 0, 0, 0, 0));
        verifyNoInteractions(memberRepository, notificationService);
    }

    @Test
    void createsOneBundledNotificationForEachActiveMember() {
        givenEventCount(3L);
        PageRequest firstPage = PageRequest.of(0, 100);
        given(memberRepository.findMemberIdsByStatusAfter(
                MemberStatus.ACTIVE, 0L, firstPage))
                .willReturn(List.of(11L, 12L));
        given(memberRepository.findMemberIdsByStatusAfter(
                MemberStatus.ACTIVE, 12L, firstPage))
                .willReturn(List.of());
        given(notificationService.createOnce(
                any(), any(), any(), any(), any(), any(), any()))
                .willReturn(true, false);

        NewEventDigestNotificationSummary summary =
                service.createForReferenceDate(REFERENCE_DATE);

        assertThat(summary).isEqualTo(
                new NewEventDigestNotificationSummary(3L, 2, 1, 1, 0));
        verify(notificationService).createOnce(
                11L,
                NotificationType.NEW_EVENTS_ADDED,
                "새로운 행사가 3개 추가되었어요!",
                "정보 탭에서 새로 등록된 행사를 확인해 보세요.",
                NotificationReferenceType.EVENT_LIST,
                "2026-11-02",
                "2026-11-02");
    }

    @Test
    void usesSingularTitleAndContinuesAfterMemberFailure() {
        givenEventCount(1L);
        PageRequest firstPage = PageRequest.of(0, 100);
        given(memberRepository.findMemberIdsByStatusAfter(
                MemberStatus.ACTIVE, 0L, firstPage))
                .willReturn(List.of(11L, 12L));
        given(memberRepository.findMemberIdsByStatusAfter(
                MemberStatus.ACTIVE, 12L, firstPage))
                .willReturn(List.of());
        given(notificationService.createOnce(
                11L,
                NotificationType.NEW_EVENTS_ADDED,
                "새로운 행사가 추가되었어요!",
                "정보 탭에서 새로 등록된 행사를 확인해 보세요.",
                NotificationReferenceType.EVENT_LIST,
                "2026-11-02",
                "2026-11-02"))
                .willThrow(new IllegalStateException("알림 생성 실패"));
        given(notificationService.createOnce(
                12L,
                NotificationType.NEW_EVENTS_ADDED,
                "새로운 행사가 추가되었어요!",
                "정보 탭에서 새로 등록된 행사를 확인해 보세요.",
                NotificationReferenceType.EVENT_LIST,
                "2026-11-02",
                "2026-11-02"))
                .willReturn(true);

        NewEventDigestNotificationSummary summary =
                service.createForReferenceDate(REFERENCE_DATE);

        assertThat(summary).isEqualTo(
                new NewEventDigestNotificationSummary(1L, 2, 1, 0, 1));
    }

    @Test
    void rerunCreatesOnlyPreviouslyFailedMemberNotification() {
        givenEventCount(1L);
        PageRequest firstPage = PageRequest.of(0, 100);
        given(memberRepository.findMemberIdsByStatusAfter(
                MemberStatus.ACTIVE, 0L, firstPage))
                .willReturn(List.of(11L, 12L), List.of(11L, 12L));
        given(memberRepository.findMemberIdsByStatusAfter(
                MemberStatus.ACTIVE, 12L, firstPage))
                .willReturn(List.of());
        given(notificationService.createOnce(
                11L,
                NotificationType.NEW_EVENTS_ADDED,
                "새로운 행사가 추가되었어요!",
                "정보 탭에서 새로 등록된 행사를 확인해 보세요.",
                NotificationReferenceType.EVENT_LIST,
                "2026-11-02",
                "2026-11-02"))
                .willThrow(new IllegalStateException("최초 알림 생성 실패"))
                .willReturn(true);
        given(notificationService.createOnce(
                12L,
                NotificationType.NEW_EVENTS_ADDED,
                "새로운 행사가 추가되었어요!",
                "정보 탭에서 새로 등록된 행사를 확인해 보세요.",
                NotificationReferenceType.EVENT_LIST,
                "2026-11-02",
                "2026-11-02"))
                .willReturn(true, false);

        NewEventDigestNotificationSummary initial =
                service.createForReferenceDate(REFERENCE_DATE);
        NewEventDigestNotificationSummary reconciliation =
                service.createForReferenceDate(REFERENCE_DATE);

        assertThat(initial).isEqualTo(
                new NewEventDigestNotificationSummary(1L, 2, 1, 0, 1));
        assertThat(reconciliation).isEqualTo(
                new NewEventDigestNotificationSummary(1L, 2, 1, 1, 0));
    }

    private void givenEventCount(long count) {
        given(newEventQuery.countByCreatedAt(
                REFERENCE_DATE.atStartOfDay(),
                REFERENCE_DATE.plusDays(1).atStartOfDay()))
                .willReturn(count);
    }
}
