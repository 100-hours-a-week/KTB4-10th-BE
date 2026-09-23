package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.member.dto.response.MemberResponse;
import com.ktb10.kgb.member.dto.response.MemberSettingsResponse;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 기본 정보와 회원 소유 데이터를 조회합니다. */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public MemberService(
            MemberRepository memberRepository,
            NotificationRepository notificationRepository,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
        long unreadCount = notificationRepository.countByRecipientId(memberId);
        return MemberResponse.of(member, unreadCount);
    }

    @Transactional(readOnly = true)
    public MemberSettingsResponse getSettings(Long memberId) {
        return MemberSettingsResponse.from(requireMember(memberId));
    }

    @Transactional
    public MemberSettingsResponse updatePushEnabled(Long memberId, boolean pushEnabled) {
        Member member = memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
        member.changePushEnabled(pushEnabled, LocalDateTime.now(clock));
        return MemberSettingsResponse.from(member);
    }

    private Member requireMember(Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
    }
}
