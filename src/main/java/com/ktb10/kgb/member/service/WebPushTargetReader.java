package com.ktb10.kgb.member.service;

import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 설정과 구독 상태를 다시 확인해 전송 가능한 구독 스냅샷을 조회합니다. */
@Service
public class WebPushTargetReader {

    private final MemberRepository memberRepository;
    private final WebPushSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public WebPushTargetReader(
            MemberRepository memberRepository,
            WebPushSubscriptionRepository subscriptionRepository,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WebPushDeliveryTarget> findDeliverableTargets(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId).orElse(null);
        if (member == null
                || member.getStatus() != MemberStatus.ACTIVE
                || !member.isPushEnabled()) {
            return List.of();
        }

        return subscriptionRepository.findDeliverableByMemberId(
                        memberId,
                        WebPushSubscriptionStatus.ACTIVE,
                        LocalDateTime.now(clock))
                .stream()
                .map(subscription -> new WebPushDeliveryTarget(
                        subscription.getId(),
                        subscription.getEndpoint(),
                        subscription.getP256dh(),
                        subscription.getAuthSecret()))
                .toList();
    }
}
