package com.ktb10.kgb.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.entity.Region;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.guidebook.repository.RegionRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationOccurrenceRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import com.ktb10.kgb.member.service.TripStartingNotificationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TripStartingNotificationMysqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 0, 0);
    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 18);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private TripStartingNotificationService tripStartingNotificationService;

    @Autowired
    private NotificationOccurrenceRepository occurrenceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberGuidebookRepository memberGuidebookRepository;

    @Autowired
    private GuidebookRepository guidebookRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        occurrenceRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        memberGuidebookRepository.deleteAllInBatch();
        guidebookRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        regionRepository.deleteAllInBatch();
    }

    @Test
    void concurrentInstancesCreateOnlyOneDurableOccurrence() throws Exception {
        Region region = regionRepository.saveAndFlush(Region.create("11", "서울"));
        Member member = activeMember("trip-starting-concurrent");
        Guidebook guidebook = guidebook(region.getId(), START_DATE, "서울 여행");
        memberGuidebookRepository.saveAndFlush(MemberGuidebook.create(
                member,
                guidebook,
                AcquisitionType.CREATED,
                NOW));

        runConcurrently(2);

        assertThat(notificationRepository.findAll())
                .singleElement()
                .extracting(Notification::getType)
                .isEqualTo(NotificationType.TRIP_STARTING);
        assertThat(occurrenceRepository.count()).isOne();

        notificationRepository.deleteAllInBatch();
        tripStartingNotificationService.createForStartDate(START_DATE);

        assertThat(notificationRepository.count()).isZero();
        assertThat(occurrenceRepository.count()).isOne();
    }

    @Test
    void excludesImportedDeletedAndInactiveGuidebooks() {
        Region region = regionRepository.saveAndFlush(Region.create("26", "부산"));
        Member eligible = activeMember("trip-starting-eligible");
        Member imported = activeMember("trip-starting-imported");
        Member deletedRelation = activeMember("trip-starting-deleted-relation");
        Member onboarding = memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                "trip-starting-onboarding",
                "온보딩 회원",
                null,
                null,
                NOW));
        Member withdrawn = activeMember("trip-starting-withdrawn");
        withdrawn.withdraw("withdrawn:" + withdrawn.getId(), NOW.plusMinutes(1));
        memberRepository.saveAndFlush(withdrawn);

        saveMemberGuidebook(eligible, guidebook(region.getId(), START_DATE, "대상 여행"),
                AcquisitionType.CREATED, false);
        saveMemberGuidebook(imported, guidebook(region.getId(), START_DATE, "가져온 여행"),
                AcquisitionType.IMPORTED, false);
        saveMemberGuidebook(
                deletedRelation,
                guidebook(region.getId(), START_DATE, "삭제된 여행"),
                AcquisitionType.CREATED,
                true);
        saveMemberGuidebook(onboarding, guidebook(region.getId(), START_DATE, "미완료 여행"),
                AcquisitionType.CREATED, false);
        saveMemberGuidebook(withdrawn, guidebook(region.getId(), START_DATE, "탈퇴 여행"),
                AcquisitionType.CREATED, false);
        saveMemberGuidebook(
                eligible,
                guidebook(region.getId(), START_DATE.plusDays(1), "다른 날짜 여행"),
                AcquisitionType.CREATED,
                false);

        tripStartingNotificationService.createForStartDate(START_DATE);

        assertThat(notificationRepository.findAll())
                .singleElement()
                .extracting(notification -> notification.getRecipient().getId())
                .isEqualTo(eligible.getId());
    }

    private Member activeMember(String oauthSubject) {
        Member member = Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1));
        member.activate(NOW);
        return memberRepository.saveAndFlush(member);
    }

    private Guidebook guidebook(Long regionId, LocalDate startDate, String title) {
        return guidebookRepository.saveAndFlush(Guidebook.create(
                title,
                regionId,
                startDate,
                startDate.plusDays(1),
                Companion.ALONE,
                1,
                "<html>완성된 가이드북</html>",
                NOW));
    }

    private void saveMemberGuidebook(
            Member member,
            Guidebook guidebook,
            AcquisitionType acquisitionType,
            boolean deleted) {
        MemberGuidebook memberGuidebook = MemberGuidebook.create(
                member,
                guidebook,
                acquisitionType,
                NOW);
        if (deleted) {
            memberGuidebook.softDelete(NOW.plusMinutes(1));
        }
        memberGuidebookRepository.saveAndFlush(memberGuidebook);
    }

    private void runConcurrently(int instanceCount) throws Exception {
        CountDownLatch ready = new CountDownLatch(instanceCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < instanceCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 실행 시작 신호를 기다리는 시간이 초과되었습니다.");
                    }
                    tripStartingNotificationService.createForStartDate(START_DATE);
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        }
    }
}
