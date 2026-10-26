package com.ktb10.kgb.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles({"local", "loadtest"})
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:loadtest-fixtures;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "map.common-cache-preload-enabled=false",
    "load-test.user-count=2",
    "load-test.credit-balance=10",
    "load-test.session-prefix=test-session-"
})
class LoadTestFixtureSeederTest extends com.ktb10.kgb.support.RedisSessionTestSupport {

    @org.junit.jupiter.api.BeforeEach
    void seedSessionsAfterRedisCleanup() {
        fixtureSeeder.run(new DefaultApplicationArguments(new String[0]));
    }

    @Autowired
    private LoadTestFixtureSeeder fixtureSeeder;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberPreferenceRepository memberPreferenceRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private com.ktb10.kgb.member.service.ServiceSessionService serviceSessions;
    @Autowired
    private com.ktb10.kgb.member.session.ServiceSessionLifecycleStore lifecycle;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Test
    @Transactional
    void preparesAuthenticatedMembersWithPreferencesAndCredits() {
        for (int sequence = 1; sequence <= 2; sequence++) {
            String suffix = String.format("%03d", sequence);
            var member = memberRepository
                    .findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                            OauthProvider.KAKAO,
                            "load-test-user-" + suffix)
                    .orElseThrow();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(memberPreferenceRepository
                    .findAllByMemberIdOrderByPreferenceTypeAscPreferenceCodeAscIdAsc(
                            member.getId()))
                    .hasSize(3);
            assertThat(creditWalletRepository.findByMemberId(member.getId()))
                    .get()
                    .extracting(wallet -> wallet.getCreditBalance())
                    .isEqualTo(10);
            assertThat(serviceSessions.authenticate("test-session-" + suffix))
                    .isPresent();
        }
    }

    @Test
    @Transactional
    void reusesMembersWithoutDuplicatingFixtureData() throws Exception {
        fixtureSeeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(memberRepository.count()).isEqualTo(2);
        assertThat(memberPreferenceRepository.count()).isEqualTo(6);
        assertThat(creditWalletRepository.count()).isEqualTo(2);
        assertThat(serviceSessions.authenticate("test-session-001")).isPresent();
        assertThat(serviceSessions.authenticate("test-session-002")).isPresent();
    }

    @Test
    @Transactional
    void revokesPreviousActiveSessionWhenFixtureSessionPrefixChanges() throws Exception {
        Member member = memberRepository
                .findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                        OauthProvider.KAKAO,
                        "load-test-user-001")
                .orElseThrow();
        LocalDateTime issuedAt = LocalDateTime.now().minusMinutes(1);
        lifecycle.issueReplacingForMember(sessionIdHasher.hash("previous-prefix-001"), member.getId(),
                java.time.Duration.ofDays(30), java.time.Duration.ofDays(30));

        fixtureSeeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(serviceSessions.authenticate("previous-prefix-001")).isEmpty();
        assertThat(serviceSessions.authenticate("test-session-001")).isPresent();
    }
}
