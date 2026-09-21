package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.service.SignupCreditGrantService;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 생성·무료 생성권 지급·기존 세션 폐기·새 세션 저장을 한 트랜잭션으로 처리합니다. */
@Service
public class OauthLoginTransactionService {

    private static final Duration ABSOLUTE_SESSION_LIFETIME = Duration.ofHours(8);

    private final MemberRepository memberRepository;
    private final AuthSessionRepository authSessionRepository;
    private final SignupCreditGrantService signupCreditGrantService;
    private final SessionIdHasher sessionIdHasher;
    private final Clock clock;

    public OauthLoginTransactionService(
            MemberRepository memberRepository,
            AuthSessionRepository authSessionRepository,
            SignupCreditGrantService signupCreditGrantService,
            SessionIdHasher sessionIdHasher,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.authSessionRepository = authSessionRepository;
        this.signupCreditGrantService = signupCreditGrantService;
        this.sessionIdHasher = sessionIdHasher;
        this.clock = clock;
    }

    @Transactional
    public OauthLoginResult complete(KakaoOauthUser oauthUser, String rawSessionId) {
        Member member = findOrRegister(oauthUser);
        Member lockedMember = memberRepository.findActiveByIdForUpdate(member.getId())
                .orElseThrow(() -> new IllegalStateException("로그인할 회원을 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now(clock);
        authSessionRepository.revokeAllActiveByMemberId(lockedMember.getId(), now);
        authSessionRepository.save(AuthSession.issue(
                lockedMember,
                sessionIdHasher.hash(rawSessionId),
                now.plus(ABSOLUTE_SESSION_LIFETIME),
                now));

        return new OauthLoginResult(rawSessionId, lockedMember.getStatus());
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
