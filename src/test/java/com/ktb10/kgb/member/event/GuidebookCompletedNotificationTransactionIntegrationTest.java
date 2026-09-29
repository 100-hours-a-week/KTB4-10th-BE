package com.ktb10.kgb.member.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.guidebook.event.GuidebookCompletedEvent;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationReferenceType;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-event-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(GuidebookCompletedNotificationTransactionIntegrationTest.EventPublisherConfiguration.class)
class GuidebookCompletedNotificationTransactionIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionalTestEventPublisher eventPublisher;

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void savesNotificationInNewTransactionAfterGuidebookTransactionCommits() {
        Member member = memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                "completed-notification-member",
                "여행자",
                null,
                null,
                NOW));

        eventPublisher.publish(new GuidebookCompletedEvent(
                member.getId(),
                501L,
                "경주 여행"));

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.getFirst();
        assertThat(notification.getRecipient().getId()).isEqualTo(member.getId());
        assertThat(notification.getType()).isEqualTo(NotificationType.GUIDEBOOK_COMPLETED);
        assertThat(notification.getTitle()).isEqualTo("가이드북 생성 완료");
        assertThat(notification.getBody()).isEqualTo("'경주 여행' 가이드북이 완성되었습니다.");
        assertThat(notification.getReferenceType()).isEqualTo(NotificationReferenceType.GUIDEBOOK);
        assertThat(notification.getReferenceId()).isEqualTo("501");
    }

    @TestConfiguration
    static class EventPublisherConfiguration {

        @Bean
        TransactionalTestEventPublisher transactionalTestEventPublisher(
                ApplicationEventPublisher eventPublisher) {
            return new TransactionalTestEventPublisher(eventPublisher);
        }
    }

    static class TransactionalTestEventPublisher {

        private final ApplicationEventPublisher eventPublisher;

        TransactionalTestEventPublisher(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Transactional
        public void publish(GuidebookCompletedEvent event) {
            eventPublisher.publishEvent(event);
        }
    }
}
