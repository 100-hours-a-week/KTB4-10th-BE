package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.service.SignupCreditGrantService;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.service.NotificationRealtimePublisher;
import com.ktb10.kgb.member.service.NotificationStreamService;
import com.ktb10.kgb.member.session.ServiceSessionLifecycleStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 회원 DB 작업 중 Redis 세션을 발급하고 DB 롤백 시 새 세션을 폐기합니다. */
@Service
public class OauthLoginTransactionService {

    private static final Duration ABSOLUTE_SESSION_LIFETIME = Duration.ofHours(8);

    private final MemberRepository memberRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(OauthLoginTransactionService.class);
    private final ServiceSessionLifecycleStore sessionStore;
    private final Duration idleTimeout;
    private final SignupCreditGrantService signupCreditGrantService;
    private final SessionIdHasher sessionIdHasher;
    private final Clock clock;
    private final NotificationStreamService streams;
    private final NotificationRealtimePublisher realtimePublisher;

    public OauthLoginTransactionService(
            MemberRepository memberRepository,
            ServiceSessionLifecycleStore sessionStore,
            @Value("${auth.session.idle-timeout:30m}") Duration idleTimeout,
            SignupCreditGrantService signupCreditGrantService,
            SessionIdHasher sessionIdHasher,
            Clock clock,
            NotificationStreamService streams,
            NotificationRealtimePublisher realtimePublisher) {
        this.memberRepository = memberRepository;
        this.sessionStore = sessionStore;
        this.idleTimeout = idleTimeout;
        this.signupCreditGrantService = signupCreditGrantService;
        this.sessionIdHasher = sessionIdHasher;
        this.clock = clock;
        this.streams = streams;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public OauthLoginResult complete(KakaoOauthUser oauthUser, String rawSessionId) {
        Member member = findOrRegister(oauthUser);
        Member lockedMember = memberRepository.findActiveByIdForUpdate(member.getId())
                .orElseThrow(() -> new IllegalStateException("로그인할 회원을 찾을 수 없습니다."));

        byte[] sessionHash = sessionIdHasher.hash(rawSessionId);
        registerRollbackCleanup(sessionHash, lockedMember.getId());
        var issued = sessionStore.issueReplacingForMember(sessionHash, lockedMember.getId(),
                ABSOLUTE_SESSION_LIFETIME, idleTimeout);
        issued.replacedSessionHash().ifPresent(previousHash ->
                registerReplacedSessionDisconnect(lockedMember.getId(), previousHash));

        return new OauthLoginResult(rawSessionId, lockedMember.getStatus());
    }

    private void registerReplacedSessionDisconnect(Long memberId, String previousHash) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // Redis에서 이미 교체된 이전 세션은 DB 롤백으로 복원되지 않습니다.
                try {
                    streams.disconnectSession(memberId, previousHash);
                } catch (RuntimeException exception) {
                    LOGGER.warn("재로그인 후 이전 로컬 SSE 종료에 실패했습니다. memberId={}", memberId, exception);
                }
                try {
                    realtimePublisher.publishSessionDisconnected(memberId, previousHash);
                } catch (RuntimeException exception) {
                    LOGGER.warn("재로그인 후 이전 SSE 종료 전파에 실패했습니다. memberId={}", memberId, exception);
                }
            }
        });
    }

    private void registerRollbackCleanup(byte[] hash, Long memberId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        sessionStore.revokeCurrent(hash, memberId);
                    } catch (RuntimeException exception) {
                        LOGGER.error("로그인 롤백 후 Redis 세션 정리에 실패했습니다. memberId={}", memberId, exception);
                    }
                }
            }
        });
    }

    private Member findOrRegister(KakaoOauthUser oauthUser) {
        requireSubject(oauthUser.subject());
        return memberRepository.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                        OauthProvider.KAKAO,
                        oauthUser.subject())
                .orElseGet(() -> register(oauthUser));
    }

    private Member register(KakaoOauthUser oauthUser) {
        if (oauthUser.nickname() == null || oauthUser.nickname().isBlank()) {
            throw new IllegalArgumentException("신규 가입에 필요한 카카오 닉네임이 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Member member = memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                oauthUser.subject(),
                oauthUser.nickname(),
                oauthUser.email(),
                oauthUser.profileImageUrl(),
                now));
        signupCreditGrantService.grantSignupMonth(member);
        return member;
    }

    private static void requireSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("카카오 사용자 식별자가 없습니다.");
        }
    }
}
