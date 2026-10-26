package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.security.AuthenticatedMember;
import com.ktb10.kgb.common.security.SessionHash;
import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.WebPushSubscriptionStatus;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import com.ktb10.kgb.member.session.ServiceSessionAuthenticationStore;
import com.ktb10.kgb.member.session.ServiceSessionLifecycleStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 세션은 Redis에서, 최신 회원 상태는 MySQL에서 조회합니다. */
@Service
public class ServiceSessionService {

    private final ServiceSessionAuthenticationStore authenticationStore;
    private final ServiceSessionLifecycleStore lifecycleStore;
    private final MemberRepository memberRepository;
    private final WebPushSubscriptionRepository subscriptionRepository;
    private final SessionIdHasher hasher;
    private final Clock clock;
    private final Duration idleTimeout;

    public ServiceSessionService(ServiceSessionAuthenticationStore authenticationStore,
            ServiceSessionLifecycleStore lifecycleStore, MemberRepository memberRepository,
            WebPushSubscriptionRepository subscriptionRepository, SessionIdHasher hasher,
            Clock clock, @Value("${auth.session.idle-timeout:30m}") Duration idleTimeout) {
        this.authenticationStore = authenticationStore;
        this.lifecycleStore = lifecycleStore;
        this.memberRepository = memberRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.hasher = hasher;
        this.clock = clock;
        this.idleTimeout = idleTimeout;
    }

    public Optional<AuthenticatedMember> authenticate(String rawSessionId) {
        byte[] hash = hasher.hash(rawSessionId);
        return authenticationStore.authenticateAndRecordUse(hash, idleTimeout)
                .flatMap(memberRepository::findByIdAndDeletedAtIsNull)
                .map(member -> new AuthenticatedMember(
                        member.getId(), member.getStatus(), HexFormat.of().formatHex(hash)));
    }

    @Transactional
    public void revokeCurrent(Long memberId, String sessionHash) {
        SessionHash.requireValid(sessionHash);
        lockMember(memberId);
        lifecycleStore.revokeCurrent(HexFormat.of().parseHex(sessionHash), memberId);
        subscriptionRepository.revokeAllActiveByMemberIdAndSessionHash(
                memberId, sessionHash, WebPushSubscriptionStatus.ACTIVE,
                WebPushSubscriptionStatus.REVOKED, LocalDateTime.now(clock));
    }

    /** 로그인/탈퇴/구독 변경과 같은 회원 행 잠금으로 직렬화합니다. */
    private Member lockMember(Long memberId) {
        return memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_SESSION_REQUIRED));
    }
}
