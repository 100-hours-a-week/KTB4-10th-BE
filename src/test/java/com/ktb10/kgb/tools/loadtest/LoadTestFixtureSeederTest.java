package com.ktb10.kgb.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.common.security.SessionIdHasher;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.member.entity.MemberStatus;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.AuthSessionRepository;
import com.ktb10.kgb.member.repository.MemberPreferenceRepository;
import com.ktb10.kgb.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"local", "loadtest"})
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:loadtest-fixtures;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "load-test.user-count=2",
    "load-test.credit-balance=10",
    "load-test.session-prefix=test-session-"
})
class LoadTestFixtureSeederTest {

    @Autowired
    private LoadTestFixtureSeeder fixtureSeeder;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberPreferenceRepository memberPreferenceRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private SessionIdHasher sessionIdHasher;

    @Test
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
            assertThat(authSessionRepository.findBySessionIdHash(
                    sessionIdHasher.hash("test-session-" + suffix)))
                    .isPresent();
        }
    }

    @Test
    void reusesMembersWithoutDuplicatingFixtureData() throws Exception {
        fixtureSeeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(memberRepository.count()).isEqualTo(2);
        assertThat(memberPreferenceRepository.count()).isEqualTo(6);
        assertThat(creditWalletRepository.count()).isEqualTo(2);
        assertThat(authSessionRepository.count()).isEqualTo(2);
    }
}
