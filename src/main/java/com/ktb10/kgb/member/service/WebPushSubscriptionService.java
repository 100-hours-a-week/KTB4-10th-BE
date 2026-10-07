package com.ktb10.kgb.member.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.member.dto.request.WebPushSubscriptionRequest;
import com.ktb10.kgb.member.dto.response.WebPushSubscriptionResponse;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.error.MemberErrorCode;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원의 브라우저별 Web Push 구독 등록·갱신·폐기를 처리합니다. */
@Service
public class WebPushSubscriptionService {

    private static final int P256DH_KEY_BYTES = 65;
    private static final int AUTH_SECRET_BYTES = 16;
    private static final byte UNCOMPRESSED_POINT_PREFIX = 0x04;

    private final WebPushSubscriptionRepository subscriptionRepository;
    private final AuthSessionRepository authSessionRepository;
    private final WebPushEndpointHasher endpointHasher;
    private final Clock clock;

    public WebPushSubscriptionService(
            WebPushSubscriptionRepository subscriptionRepository,
            AuthSessionRepository authSessionRepository,
            WebPushEndpointHasher endpointHasher,
            Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.authSessionRepository = authSessionRepository;
        this.endpointHasher = endpointHasher;
        this.clock = clock;
    }

    @Transactional
    public WebPushSubscriptionResponse registerOrRenew(
            Long memberId,
            Long sessionId,
            WebPushSubscriptionRequest request) {
        validateRequest(request);
        AuthSession authSession = authSessionRepository
                .findByIdAndMemberIdForPushRegistration(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.WEB_PUSH_SUBSCRIPTION_INVALID));
        Member member = authSession.getMember();
        byte[] endpointHash = endpointHasher.hash(request.endpoint());
        LocalDateTime expirationAt = expirationAt(request.expirationTime());
        LocalDateTime now = LocalDateTime.now(clock);

        WebPushSubscription subscription = subscriptionRepository
                .findByEndpointHash(endpointHash)
                .map(existing -> renewExisting(
                        existing,
                        member,
                        authSession,
                        request,
                        expirationAt,
                        now))
                .orElseGet(() -> WebPushSubscription.register(
                        member,
                        authSession,
                        request.endpoint(),
                        endpointHash,
                        request.keys().p256dh(),
                        request.keys().auth(),
                        expirationAt,
                        now));

        return WebPushSubscriptionResponse.from(subscriptionRepository.saveAndFlush(subscription));
    }

    @Transactional
    public void revoke(Long memberId, Long subscriptionId) {
        WebPushSubscription subscription = subscriptionRepository
                .findByIdAndMemberId(subscriptionId, memberId)
                .orElseThrow(() -> new BusinessException(
                        MemberErrorCode.WEB_PUSH_SUBSCRIPTION_NOT_FOUND));
        subscription.revoke(LocalDateTime.now(clock));
    }

    private WebPushSubscription renewExisting(
            WebPushSubscription existing,
            Member member,
            AuthSession authSession,
            WebPushSubscriptionRequest request,
            LocalDateTime expirationAt,
            LocalDateTime now) {
        if (existing.isActive() && !existing.isOwnedBy(member.getId())) {
            throw new BusinessException(MemberErrorCode.WEB_PUSH_SUBSCRIPTION_CONFLICT);
        }
        existing.renew(
                member,
                authSession,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth(),
                expirationAt,
                now);
        return existing;
    }

    private void validateRequest(WebPushSubscriptionRequest request) {
        if (!isValidEndpoint(request.endpoint())
                || !isValidKey(request.keys().p256dh(), P256DH_KEY_BYTES, true)
                || !isValidKey(request.keys().auth(), AUTH_SECRET_BYTES, false)) {
            throw new BusinessException(MemberErrorCode.WEB_PUSH_SUBSCRIPTION_INVALID);
        }
    }

    private boolean isValidEndpoint(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean isValidKey(String value, int expectedBytes, boolean requirePointPrefix) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == expectedBytes
                    && (!requirePointPrefix || decoded[0] == UNCOMPRESSED_POINT_PREFIX);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private LocalDateTime expirationAt(Long expirationTime) {
        if (expirationTime == null) {
            return null;
        }
        try {
            LocalDateTime expirationAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(expirationTime),
                    ZoneOffset.UTC);
            if (!expirationAt.isAfter(LocalDateTime.now(clock))) {
                throw new BusinessException(MemberErrorCode.WEB_PUSH_SUBSCRIPTION_INVALID);
            }
            return expirationAt;
        } catch (DateTimeException exception) {
            throw new BusinessException(MemberErrorCode.WEB_PUSH_SUBSCRIPTION_INVALID);
        }
    }
}
