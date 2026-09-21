package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.member.dto.response.MemberResponse;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 기본 정보와 회원 소유 데이터를 조회합니다. */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;

    public MemberService(
            MemberRepository memberRepository,
            NotificationRepository notificationRepository) {
        this.memberRepository = memberRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
        long unreadCount = notificationRepository.countByRecipientId(memberId);
        return MemberResponse.of(member, unreadCount);
    }
}
