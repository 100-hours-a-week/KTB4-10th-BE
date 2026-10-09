package com.ktb10.kgb.tools.loadtest;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 로컬 부하 테스트에 필요한 회원, 세션, 취향과 생성권 fixture를 준비합니다. */
@Component
@Profile("local & loadtest")
public class LoadTestFixtureSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadTestFixtureSeeder.class);
    private static final int MAX_USER_COUNT = 2_000;
    private static final List<PreferenceCode> DEFAULT_PREFERENCES = List.of(
            PreferenceCode.NATURE,
            PreferenceCode.NATURE_PARK,
            PreferenceCode.RELAXING);

    private final MemberRepository memberRepository;
    private final MemberPreferenceRepository memberPreferenceRepository;
    private final CreditWalletRepository creditWalletRepository;
    private final AuthSessionRepository authSessionRepository;
    private final SessionIdHasher sessionIdHasher;
    private final Optional<LoadTestSessionAuthenticationBypass> authenticationBypass;
    private final int userCount;
    private final int creditBalance;
    private final String sessionPrefix;

    public LoadTestFixtureSeeder(
            MemberRepository memberRepository,
            MemberPreferenceRepository memberPreferenceRepository,
            CreditWalletRepository creditWalletRepository,
            AuthSessionRepository authSessionRepository,
            SessionIdHasher sessionIdHasher,
            Optional<LoadTestSessionAuthenticationBypass> authenticationBypass,
            @Value("${load-test.user-count}") int userCount,
            @Value("${load-test.credit-balance}") int creditBalance,
            @Value("${load-test.session-prefix}") String sessionPrefix) {
        this.memberRepository = memberRepository;
        this.memberPreferenceRepository = memberPreferenceRepository;
        this.creditWalletRepository = creditWalletRepository;
        this.authSessionRepository = authSessionRepository;
        this.sessionIdHasher = sessionIdHasher;
        this.authenticationBypass = authenticationBypass;
        this.userCount = validateUserCount(userCount);
        this.creditBalance = validateCreditBalance(creditBalance);
        this.sessionPrefix = validateSessionPrefix(sessionPrefix);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        authenticationBypass.ifPresent(LoadTestSessionAuthenticationBypass::clear);
        for (int sequence = 1; sequence <= userCount; sequence++) {
            Member member = prepareMember(sequence, now);
            preparePreferences(member);
            prepareWallet(member, now);
            String rawSessionId = rawSessionId(sequence);
            AuthSession session = replaceSession(member, rawSessionId, now);
            authenticationBypass.ifPresent(bypass -> bypass.replace(
                    rawSessionId,
                    member.getId(),
                    session.getId()));
        }

        LOGGER.info(
                "Local load-test fixtures prepared [userCount={}, sessionPrefix={}]",
                userCount,
                sessionPrefix);
    }

    private Member prepareMember(int sequence, LocalDateTime now) {
        String suffix = formattedSequence(sequence);
        String oauthSubject = "load-test-user-" + suffix;
        Member member = memberRepository
                .findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                        OauthProvider.KAKAO,
                        oauthSubject)
                .orElseGet(() -> memberRepository.save(Member.register(
                        OauthProvider.KAKAO,
                        oauthSubject,
                        "부하테스트" + suffix,
                        null,
                        null,
                        now)));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            member.activate(now);
        }
        return member;
    }

    private void preparePreferences(Member member) {
        for (PreferenceCode preferenceCode : DEFAULT_PREFERENCES) {
            boolean exists = memberPreferenceRepository
                    .existsByMemberIdAndPreferenceTypeAndPreferenceCode(
                            member.getId(),
                            preferenceCode.type(),
                            preferenceCode);
            if (!exists) {
                memberPreferenceRepository.save(MemberPreference.select(member, preferenceCode));
            }
        }
    }

    private void prepareWallet(Member member, LocalDateTime now) {
        CreditWallet wallet = creditWalletRepository.findByMemberId(member.getId())
                .orElseGet(() -> creditWalletRepository.save(CreditWallet.open(member, now)));
        int requiredCredit = creditBalance - wallet.getCreditBalance();
        if (requiredCredit > 0) {
            wallet.grant(requiredCredit, now);
        }
    }

    private AuthSession replaceSession(Member member, String rawSessionId, LocalDateTime now) {
        byte[] sessionHash = sessionIdHasher.hash(rawSessionId);
        authSessionRepository.revokeAllActiveByMemberId(member.getId(), now);
        authSessionRepository.findBySessionIdHash(sessionHash).ifPresent(existing -> {
            authSessionRepository.delete(existing);
            authSessionRepository.flush();
        });
        return authSessionRepository.save(AuthSession.issue(
                member,
                sessionHash,
                now.plusDays(30),
                now));
    }

    private String rawSessionId(int sequence) {
        return sessionPrefix + formattedSequence(sequence);
    }

    private String formattedSequence(int sequence) {
        return String.format("%03d", sequence);
    }

    private int validateUserCount(int value) {
        if (value < 1 || value > MAX_USER_COUNT) {
            throw new IllegalArgumentException("부하 테스트 회원 수는 1 이상 2000 이하여야 합니다.");
        }
        return value;
    }

    private int validateCreditBalance(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("부하 테스트 생성권은 1개 이상이어야 합니다.");
        }
        return value;
    }

    private String validateSessionPrefix(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("부하 테스트 세션 접두사는 비어 있을 수 없습니다.");
        }
        return value;
    }
}
