package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.member.entity.Member;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripStartingNotificationServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 18);

    @Mock
    private MemberGuidebookRepository memberGuidebookRepository;

    @Mock
    private NotificationService notificationService;

    private TripStartingNotificationService tripStartingNotificationService;

    @BeforeEach
    void setUp() {
        tripStartingNotificationService = new TripStartingNotificationService(
                memberGuidebookRepository,
                notificationService);
    }

    @Test
    void continuesWithNextCandidateWhenOneNotificationFails() {
        MemberGuidebook first = candidate(1L, 11L, 101L, "첫 여행");
        MemberGuidebook second = candidate(2L, 12L, 102L, "두 번째 여행");
        given(memberGuidebookRepository.findTripStartingCandidatesAfter(
                eq(START_DATE), any(), any(), eq(0L), any()))
                .willReturn(List.of(first, second));
        given(memberGuidebookRepository.findTripStartingCandidatesAfter(
                eq(START_DATE), any(), any(), eq(2L), any()))
                .willReturn(List.of());
        given(notificationService.createOnce(
                eq(11L), any(), any(), any(), any(), eq("101"), eq("2026-10-18")))
                .willThrow(new IllegalStateException("첫 알림 실패"));
        given(notificationService.createOnce(
                eq(12L), any(), any(), any(), any(), eq("102"), eq("2026-10-18")))
                .willReturn(true);

        TripStartingNotificationSummary result =
                tripStartingNotificationService.createForStartDate(START_DATE);

        assertThat(result).isEqualTo(new TripStartingNotificationSummary(1, 0, 1));
    }

    private MemberGuidebook candidate(
            Long memberGuidebookId,
            Long memberId,
            Long guidebookId,
            String title) {
        Member member = org.mockito.Mockito.mock(Member.class);
        Guidebook guidebook = org.mockito.Mockito.mock(Guidebook.class);
        MemberGuidebook memberGuidebook = org.mockito.Mockito.mock(MemberGuidebook.class);
        given(member.getId()).willReturn(memberId);
        given(guidebook.getId()).willReturn(guidebookId);
        given(guidebook.getTitle()).willReturn(title);
        given(memberGuidebook.getId()).willReturn(memberGuidebookId);
        given(memberGuidebook.getMember()).willReturn(member);
        given(memberGuidebook.getGuidebook()).willReturn(guidebook);
        return memberGuidebook;
    }
}
