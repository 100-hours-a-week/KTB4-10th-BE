package com.ktb10.kgb.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemberEntityTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 17, 9, 0);

    @Test
    void newMemberStartsOnboardingWithDefaultSettings() {
        Member member = createMember();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ONBOARDING);
        assertThat(member.getLanguageCode()).isEqualTo("ko");
        assertThat(member.isPushEnabled()).isTrue();
        assertThat(member.isDeleted()).isFalse();
    }

    @Test
    void withdrawnMemberCannotChangeSettings() {
        Member member = createMember();
        member.withdraw("withdrawn:member:1", BASE_TIME.plusHours(1));

        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getOauthSubject()).isEqualTo("withdrawn:member:1");
        assertThatThrownBy(() -> member.changePushEnabled(false, BASE_TIME.plusHours(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sessionUsesAbsoluteAndIdleExpirationAndCanBeRevoked() {
        AuthSession session = AuthSession.issue(
                createMember(),
                hash(1),
                BASE_TIME.plusHours(8),
                BASE_TIME);

        assertThat(session.isUsable(BASE_TIME.plusMinutes(29), Duration.ofMinutes(30))).isTrue();
        assertThat(session.isUsable(BASE_TIME.plusMinutes(30), Duration.ofMinutes(30))).isFalse();

        session.recordUse(BASE_TIME.plusMinutes(20));
        assertThat(session.isUsable(BASE_TIME.plusMinutes(49), Duration.ofMinutes(30))).isTrue();

        session.revoke(BASE_TIME.plusMinutes(50));
        assertThat(session.isUsable(BASE_TIME.plusMinutes(51), Duration.ofMinutes(30))).isFalse();
    }

    @Test
    void sessionHashMustBeSha256Length() {
        Member member = createMember();

        assertThatThrownBy(() -> AuthSession.issue(
                member,
                new byte[31],
                BASE_TIME.plusHours(8),
                BASE_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preferenceTypeComesFromStablePreferenceCode() {
        MemberPreference preference = MemberPreference.select(
                createMember(),
                PreferenceCode.NATURE_MOUNTAIN);

        assertThat(preference.getPreferenceType()).isEqualTo(PreferenceType.DETAIL);
        assertThat(preference.getPreferenceCode().parentCode()).isEqualTo("NATURE");
    }

    @Test
    void notificationReferenceTypeAndIdMustFormPair() {
        Member member = createMember();

        assertThatThrownBy(() -> Notification.create(
                member,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 완성",
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                null,
                BASE_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Member createMember() {
        return Member.register(
                OauthProvider.KAKAO,
                "oauth-subject",
                "여행자",
                null,
                null,
                BASE_TIME);
    }

    private static byte[] hash(int marker) {
        byte[] hash = new byte[32];
        hash[0] = (byte) marker;
        return hash;
    }
}
