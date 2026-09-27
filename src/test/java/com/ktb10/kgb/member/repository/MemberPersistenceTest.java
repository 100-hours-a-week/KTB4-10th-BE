package com.ktb10.kgb.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb10.kgb.member.entity.AuthSession;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.MemberPreference;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.entity.PreferenceCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MemberPersistenceTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 17, 9, 0);

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private MemberPreferenceRepository memberPreferenceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void activeMemberCanBeFoundByOauthKeyAndSoftDeletedMemberIsExcluded() {
        Member member = memberRepository.save(member("member-1"));

        assertThat(memberRepository.findByOauthProviderAndOauthSubjectAndDeletedAtIsNull(
                OauthProvider.KAKAO,
                "member-1"))
                .contains(member);

        member.withdraw("withdrawn:" + member.getId(), BASE_TIME.plusHours(1));
        memberRepository.flush();

        assertThat(memberRepository.findByIdAndDeletedAtIsNull(member.getId())).isEmpty();
    }

    @Test
    void sessionCanBeFoundByHashAndAllActiveSessionsCanBeRevoked() {
        Member member = memberRepository.save(member("member-2"));
        AuthSession first = AuthSession.issue(
                member,
                hash(1),
                BASE_TIME.plusHours(8),
                BASE_TIME);
        AuthSession second = AuthSession.issue(
                member,
                hash(2),
                BASE_TIME.plusHours(8),
                BASE_TIME.plusMinutes(1));
        authSessionRepository.saveAllAndFlush(List.of(first, second));

        assertThat(authSessionRepository.findBySessionIdHash(hash(1))).contains(first);
        assertThat(authSessionRepository
                .findAllByMemberIdAndRevokedAtIsNullOrderByCreatedAtAsc(member.getId()))
                .containsExactly(first, second);

        int revoked = authSessionRepository.revokeAllActiveByMemberId(
                member.getId(),
                BASE_TIME.plusHours(1));
        entityManager.clear();

        assertThat(revoked).isEqualTo(2);
        assertThat(authSessionRepository
                .findAllByMemberIdAndRevokedAtIsNullOrderByCreatedAtAsc(member.getId()))
                .isEmpty();
    }

    @Test
    void duplicatePreferenceSelectionIsRejectedAndSelectionsCanBeReplaced() {
        Member member = memberRepository.save(member("member-3"));
        MemberPreference nature = MemberPreference.select(member, PreferenceCode.NATURE);
        memberPreferenceRepository.saveAndFlush(nature);

        assertThat(memberPreferenceRepository.existsByMemberIdAndPreferenceTypeAndPreferenceCode(
                member.getId(),
                PreferenceCode.NATURE.type(),
                PreferenceCode.NATURE))
                .isTrue();

        assertThatThrownBy(() -> memberPreferenceRepository.saveAndFlush(
                MemberPreference.select(member, PreferenceCode.NATURE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notificationsArePagedNewestFirstAndDeletedOnlyWithinOwnerScope() {
        Member owner = memberRepository.save(member("member-4"));
        Member other = memberRepository.save(member("member-5"));
        Notification older = notification(owner, "older", BASE_TIME);
        Notification newer = notification(owner, "newer", BASE_TIME.plusMinutes(1));
        Notification others = notification(other, "others", BASE_TIME.plusMinutes(2));
        notificationRepository.saveAllAndFlush(List.of(older, newer, others));

        Page<Notification> page = notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        owner.getId(),
                        PageRequest.of(0, 4));

        assertThat(page.getContent()).containsExactly(newer, older);
        assertThat(notificationRepository.deleteByIdAndRecipientId(
                others.getId(),
                owner.getId()))
                .isZero();
        assertThat(notificationRepository.existsById(others.getId())).isTrue();
    }

    @Test
    void oldestNotificationIdsCanBeSelectedAndDeletedAsSnapshot() {
        Member member = memberRepository.save(member("member-6"));
        Notification first = notification(member, "first", BASE_TIME);
        Notification second = notification(member, "second", BASE_TIME.plusMinutes(1));
        Notification third = notification(member, "third", BASE_TIME.plusMinutes(2));
        notificationRepository.saveAllAndFlush(List.of(first, second, third));

        List<Long> oldestIds = notificationRepository.findOldestIdsByRecipientMemberId(
                member.getId(),
                PageRequest.of(0, 2));
        int deleted = notificationRepository.deleteAllByRecipientMemberIdAndIdIn(
                member.getId(),
                oldestIds);

        assertThat(deleted).isEqualTo(2);
        assertThat(notificationRepository.countByRecipientId(member.getId())).isEqualTo(1);
        assertThat(notificationRepository.existsById(third.getId())).isTrue();
    }

    private static Member member(String subject) {
        return Member.register(
                OauthProvider.KAKAO,
                subject,
                "여행자",
                null,
                null,
                BASE_TIME);
    }

    private static Notification notification(
            Member recipient,
            String referenceId,
            LocalDateTime createdAt) {
        return Notification.create(
                recipient,
                NotificationType.GUIDEBOOK_COMPLETED,
                "가이드북 완성",
                "가이드북을 확인해 주세요.",
                NotificationReferenceType.GUIDEBOOK,
                referenceId,
                createdAt);
    }

    private static byte[] hash(int marker) {
        byte[] hash = new byte[32];
        hash[0] = (byte) marker;
        return hash;
    }
}
