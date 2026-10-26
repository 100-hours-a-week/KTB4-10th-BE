package com.ktb10.kgb.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.WebPushSubscription;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.WebPushSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({WebPushTargetReader.class, WebPushTargetReaderTest.FixedClockConfiguration.class})
class WebPushTargetReaderTest {

    private static final Instant CURRENT_INSTANT = Instant.parse("2026-10-17T00:00:00Z");
    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(CURRENT_INSTANT, ZoneOffset.UTC);

    @Autowired
    private WebPushTargetReader targetReader;

    @Autowired
    private WebPushSubscriptionRepository subscriptionRepository;


    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void cleanUp() {
        subscriptionRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    void pushDeliveryDoesNotDependOnRedisSessionExistence() {
        Member member = activeMember("expired-session-push-target");
        String expiredSession = "0".repeat(64);
        WebPushSubscription subscription = subscriptionRepository.saveAndFlush(
                WebPushSubscription.register(
                        member,
                        expiredSession,
                        "https://fcm.googleapis.com/fcm/send/expired-session-target",
                        hashWithLastByte((byte) 1),
                        "p256dh",
                        "auth",
                        null,
                        NOW.minusHours(1)));

        assertThat(targetReader.findDeliverableTargets(member.getId()))
                .singleElement()
                .extracting(WebPushDeliveryTarget::subscriptionId)
                .isEqualTo(subscription.getId());
    }

    @Test
    void disabledMemberAndMemberWithoutSubscriptionAreSkippedWithoutFailure() {
        Member disabled = activeMember("disabled-push-target");
        disabled.changePushEnabled(false, NOW.plusMinutes(1));
        memberRepository.saveAndFlush(disabled);
        Member noSubscription = activeMember("missing-push-target");

        assertThat(targetReader.findDeliverableTargets(disabled.getId())).isEmpty();
        assertThat(targetReader.findDeliverableTargets(noSubscription.getId())).isEmpty();
    }

    private Member activeMember(String oauthSubject) {
        Member member = Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1));
        member.activate(NOW.minusHours(1));
        return memberRepository.saveAndFlush(member);
    }

    private byte[] hashWithLastByte(byte lastByte) {
        byte[] hash = new byte[32];
        hash[31] = lastByte;
        return hash;
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(CURRENT_INSTANT, ZoneOffset.UTC);
        }
    }
}
