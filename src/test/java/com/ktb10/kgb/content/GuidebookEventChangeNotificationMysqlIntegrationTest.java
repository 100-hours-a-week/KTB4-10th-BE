package com.ktb10.kgb.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ktb10.kgb.content.event.ContentChangesDetectedEvent;
import com.ktb10.kgb.content.service.GuidebookEventChangeNotificationProcessor;
import com.ktb10.kgb.content.tourapi.ContentChangedField;
import com.ktb10.kgb.content.tourapi.ContentLifecycleChange;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrence;
import com.ktb10.kgb.content.tourapi.TourApiContentChangeOccurrenceRepository;
import com.ktb10.kgb.guidebook.entity.AcquisitionType;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import com.ktb10.kgb.guidebook.entity.MemberGuidebook;
import com.ktb10.kgb.guidebook.entity.Region;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import com.ktb10.kgb.guidebook.repository.RegionRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.Notification;
import com.ktb10.kgb.member.entity.NotificationType;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import com.ktb10.kgb.member.repository.NotificationOccurrenceRepository;
import com.ktb10.kgb.member.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GuidebookEventChangeNotificationMysqlIntegrationTest {

    private static final UUID RUN_ID =
            UUID.fromString("c783905d-904f-41b9-8016-cb8242533cc8");
    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 11, 3);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private GuidebookEventChangeNotificationProcessor processor;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private GuidebookRepository guidebookRepository;

    @Autowired
    private MemberGuidebookRepository memberGuidebookRepository;

    @Autowired
    private ItineraryDayRepository itineraryDayRepository;

    @Autowired
    private ItineraryItemRepository itineraryItemRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationOccurrenceRepository notificationOccurrenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TourApiContentChangeOccurrenceRepository occurrenceRepository;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        notificationOccurrenceRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        itineraryItemRepository.deleteAllInBatch();
        itineraryDayRepository.deleteAllInBatch();
        memberGuidebookRepository.deleteAllInBatch();
        guidebookRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM event_details");
        jdbcTemplate.update("DELETE FROM tourism_contents");
        memberRepository.deleteAllInBatch();
        regionRepository.deleteAllInBatch();
    }

    @Test
    void concurrentRunsNotifyCreatedGuidebookOwnerOnlyOnce() throws Exception {
        Region region = regionRepository.saveAndFlush(Region.create("11", "서울"));
        Long contentId = insertEvent(region.getId(), "cancelled-event");
        Member creator = activeMember("guidebook-event-creator");
        Member importedMember = activeMember("guidebook-event-imported");
        Member onboarding = onboardingMember("guidebook-event-onboarding");

        Guidebook eligibleGuidebook = guidebook(region.getId(), "알림 대상 가이드북");
        saveRelationship(creator, eligibleGuidebook, AcquisitionType.CREATED, false);
        saveRelationship(importedMember, eligibleGuidebook, AcquisitionType.IMPORTED, false);
        saveItinerary(eligibleGuidebook, contentId, 1);

        Guidebook deletedGuidebook = guidebook(region.getId(), "삭제된 관계 가이드북");
        saveRelationship(creator, deletedGuidebook, AcquisitionType.CREATED, true);
        saveItinerary(deletedGuidebook, contentId, 1);

        Guidebook onboardingGuidebook = guidebook(region.getId(), "온보딩 가이드북");
        saveRelationship(onboarding, onboardingGuidebook, AcquisitionType.CREATED, false);
        saveItinerary(onboardingGuidebook, contentId, 1);

        given(occurrenceRepository.findAllByRunId(RUN_ID)).willReturn(List.of(
                occurrence(1L, "cancelled-event", ContentLifecycleChange.INACTIVATED)));

        runConcurrently(2);

        assertThat(notificationRepository.findAll())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getRecipient().getId()).isEqualTo(creator.getId());
                    assertThat(notification.getType())
                            .isEqualTo(NotificationType.GUIDEBOOK_EVENT_CANCELLED);
                    assertThat(notification.getReferenceId())
                            .isEqualTo(eligibleGuidebook.getId().toString());
                });
        assertThat(notificationOccurrenceRepository.count()).isOne();
    }

    private Long insertEvent(Long regionId, String sourceContentId) {
        jdbcTemplate.update(
                """
                INSERT INTO tourism_contents (
                    source_provider, source_content_id, source_content_type_id,
                    title, region_id, source_region_code, source_district_code,
                    classification_code_1, classification_code_2, classification_code_3,
                    address, location, status, source_created_at, source_modified_at,
                    created_at, updated_at
                ) VALUES (
                    'TOUR_API', ?, '15', ?, ?, '1', '1',
                    'EV', NULL, NULL, '서울', ST_SRID(POINT(127.1, 37.5), 4326),
                    'INACTIVE', ?, ?, ?, ?
                )
                """,
                sourceContentId,
                "취소된 행사",
                regionId,
                NOW.minusDays(10),
                NOW,
                NOW.minusDays(10),
                NOW);
        Long contentId = jdbcTemplate.queryForObject(
                "SELECT id FROM tourism_contents WHERE source_content_id = ?",
                Long.class,
                sourceContentId);
        jdbcTemplate.update(
                """
                INSERT INTO event_details (
                    content_id, start_date, end_date, updated_at
                ) VALUES (?, ?, ?, ?)
                """,
                contentId,
                SYNC_DATE.plusDays(10),
                SYNC_DATE.plusDays(11),
                NOW);
        return contentId;
    }

    private Member activeMember(String oauthSubject) {
        Member member = onboardingMember(oauthSubject);
        member.activate(NOW);
        return memberRepository.saveAndFlush(member);
    }

    private Member onboardingMember(String oauthSubject) {
        return memberRepository.saveAndFlush(Member.register(
                OauthProvider.KAKAO,
                oauthSubject,
                "여행자",
                null,
                null,
                NOW.minusDays(1)));
    }

    private Guidebook guidebook(Long regionId, String title) {
        return guidebookRepository.saveAndFlush(Guidebook.create(
                title,
                regionId,
                SYNC_DATE.plusDays(10),
                SYNC_DATE.plusDays(11),
                Companion.ALONE,
                1,
                "<html>가이드북</html>",
                NOW));
    }

    private void saveRelationship(
            Member member,
            Guidebook guidebook,
            AcquisitionType acquisitionType,
            boolean deleted) {
        MemberGuidebook relationship = MemberGuidebook.create(
                member,
                guidebook,
                acquisitionType,
                NOW);
        if (deleted) {
            relationship.softDelete(NOW.plusMinutes(1));
        }
        memberGuidebookRepository.saveAndFlush(relationship);
    }

    private void saveItinerary(Guidebook guidebook, Long contentId, int sequence) {
        ItineraryDay day = itineraryDayRepository.saveAndFlush(ItineraryDay.create(
                guidebook,
                1,
                guidebook.getStartDate()));
        itineraryItemRepository.saveAndFlush(ItineraryItem.create(
                day,
                contentId,
                sequence,
                LocalTime.of(10, 0),
                "{\"title\":\"행사\"}",
                NOW));
    }

    private TourApiContentChangeOccurrence occurrence(
            Long id,
            String sourceContentId,
            ContentLifecycleChange lifecycleChange) {
        return new TourApiContentChangeOccurrence(
                id,
                RUN_ID,
                sourceContentId,
                Set.of(ContentChangedField.STATUS),
                lifecycleChange,
                null,
                null,
                NOW,
                NOW);
    }

    private void runConcurrently(int instanceCount) throws Exception {
        ContentChangesDetectedEvent event = new ContentChangesDetectedEvent(
                RUN_ID,
                SYNC_DATE,
                1);
        CountDownLatch ready = new CountDownLatch(instanceCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < instanceCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "동시 실행 시작 신호를 기다리는 시간이 초과되었습니다.");
                    }
                    processor.process(event);
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
