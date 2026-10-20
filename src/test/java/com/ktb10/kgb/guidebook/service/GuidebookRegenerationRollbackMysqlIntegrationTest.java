package com.ktb10.kgb.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.credit.entity.CreditWallet;
import com.ktb10.kgb.credit.repository.CreditWalletRepository;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import com.ktb10.kgb.guidebook.client.AiGuidebookContentQuery;
import com.ktb10.kgb.guidebook.client.TourismContentSnapshotQuery;
import com.ktb10.kgb.guidebook.client.TourismContentSnapshotQuery.TourismContentSnapshot;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.GuidebookResult;
import com.ktb10.kgb.guidebook.client.dto.AiGenerationStatusResponse.Place;
import com.ktb10.kgb.guidebook.client.dto.AiGuidebookRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookGenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.GuidebookRegenerationRequest;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload;
import com.ktb10.kgb.guidebook.dto.request.InitialGenerationRequestPayload.PreferenceSnapshot;
import com.ktb10.kgb.guidebook.dto.request.RegenerationRequestPayload;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import com.ktb10.kgb.guidebook.entity.Region;
import com.ktb10.kgb.guidebook.repository.GenerationJobRepository;
import com.ktb10.kgb.guidebook.repository.GuidebookRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import com.ktb10.kgb.guidebook.repository.RegionRepository;
import com.ktb10.kgb.member.entity.Member;
import com.ktb10.kgb.member.entity.OauthProvider;
import com.ktb10.kgb.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class GuidebookRegenerationRollbackMysqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
    private static final LocalDate TRIP_DATE = LocalDate.of(2026, 10, 25);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private GuidebookResultService resultService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private GuidebookRepository guidebookRepository;
    @Autowired
    private ItineraryDayRepository itineraryDayRepository;
    @MockitoSpyBean
    private ItineraryItemRepository itineraryItemRepository;
    @Autowired
    private GenerationJobRepository generationJobRepository;
    @Autowired
    private RegionRepository regionRepository;
    @Autowired
    private CreditWalletRepository creditWalletRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiGuidebookContentQuery contentQuery;
    @MockitoBean
    private TourismContentSnapshotQuery contentSnapshotQuery;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void restoresPreviousItineraryWhenSavingRegeneratedItineraryFails() {
        Fixture fixture = new TransactionTemplate(transactionManager)
                .execute(status -> createFixture());

        given(contentQuery.findAll("경상북도", "경주시", TRIP_DATE, TRIP_DATE))
                .willReturn(List.of(candidate()));
        given(contentSnapshotQuery.findBySourceContentId("new-content"))
                .willReturn(java.util.Optional.of(new TourismContentSnapshot(
                        702L,
                        "new-content",
                        "동궁과 월지",
                        "HS",
                        "경상북도 경주시",
                        35.8347,
                        129.2266,
                        null,
                        null,
                        null)));
        doThrow(new IllegalStateException("새 일정 저장 실패"))
                .when(itineraryItemRepository)
                .save(any(ItineraryItem.class));

        assertThatThrownBy(() -> resultService.apply(
                        fixture.jobId(),
                        AiGenerationStatusResponse.completed(
                                "ai-regenerated",
                                regeneratedResult())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("새 일정 저장 실패");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM itinerary_days WHERE id = ?",
                Integer.class,
                fixture.dayId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM itinerary_items WHERE id = ?",
                Integer.class,
                fixture.itemId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM guidebooks WHERE id = ?",
                String.class,
                fixture.guidebookId())).isEqualTo("기존 경주 여행");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content_html FROM guidebooks WHERE id = ?",
                String.class,
                fixture.guidebookId())).isEqualTo("<article>기존 일정</article>");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM guidebooks WHERE id = ?",
                Integer.class,
                fixture.guidebookId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM generation_jobs WHERE id = ?",
                String.class,
                fixture.jobId())).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT credit_balance FROM credit_wallets WHERE member_id = ?",
                Integer.class,
                fixture.memberId())).isEqualTo(2);
    }

    private Fixture createFixture() {
        Member member = memberRepository.save(Member.register(
                OauthProvider.KAKAO,
                UUID.randomUUID().toString(),
                "여행자",
                null,
                null,
                NOW.minusDays(1)));
        Region region = regionRepository.save(Region.create(
                "47-rollback", "경상북도"));
        Guidebook guidebook = guidebookRepository.save(Guidebook.create(
                "기존 경주 여행",
                region.getId(),
                TRIP_DATE,
                TRIP_DATE,
                Companion.COUPLE,
                2,
                "<article>기존 일정</article>",
                NOW.minusDays(1)));
        ItineraryDay day = itineraryDayRepository.save(
                ItineraryDay.create(guidebook, 1, TRIP_DATE));
        ItineraryItem item = itineraryItemRepository.save(ItineraryItem.create(
                day,
                null,
                1,
                LocalTime.of(9, 0),
                "{\"content_id\":\"old-content\"}",
                NOW.minusDays(1)));

        CreditWallet wallet = CreditWallet.open(member, NOW.minusDays(1));
        wallet.grant(2, NOW.minusDays(1));
        creditWalletRepository.save(wallet);

        GenerationJob job = GenerationJob.createRegeneration(
                member,
                guidebook.getId(),
                requestPayload(),
                UUID.randomUUID().toString(),
                NOW);
        job.registerAiJob("ai-regenerated", NOW);
        generationJobRepository.save(job);

        generationJobRepository.flush();
        return new Fixture(
                member.getId(), guidebook.getId(), day.getId(), item.getId(), job.getId());
    }

    private String requestPayload() {
        GuidebookGenerationRequest sourceRequest = new GuidebookGenerationRequest(
                "경상북도",
                "경주시",
                TRIP_DATE,
                TRIP_DATE,
                Companion.COUPLE,
                2);
        try {
            return objectMapper.writeValueAsString(new RegenerationRequestPayload(
                    new GuidebookRegenerationRequest("일정을 여유롭게 바꿔 주세요."),
                    new InitialGenerationRequestPayload(
                            sourceRequest,
                            List.of(new PreferenceSnapshot("THEME", "HISTORY"))),
                    "ai-original"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("테스트 요청을 직렬화할 수 없습니다.", exception);
        }
    }

    private GuidebookResult regeneratedResult() {
        Place place = new Place(
                1,
                LocalTime.of(11, 0),
                "new-content",
                60,
                "재생성 추천 이유");
        return new GuidebookResult(
                "AI가 제안한 새 제목",
                "재생성 일정 요약",
                "<article>재생성 일정</article>",
                List.of(new AiGenerationStatusResponse.ItineraryDay(
                        1,
                        TRIP_DATE,
                        "재생성 첫날",
                        List.of(place))));
    }

    private AiGuidebookRequest.Content candidate() {
        return new AiGuidebookRequest.Content(
                "new-content",
                AiGuidebookRequest.ContentType.PLACE,
                "동궁과 월지",
                "동궁과 월지 개요",
                "HS",
                "HS01",
                "HS010100",
                "경상북도 경주시",
                new AiGuidebookRequest.Coordinates(35.8347, 129.2266),
                null,
                null);
    }

    private record Fixture(
            Long memberId,
            Long guidebookId,
            Long dayId,
            Long itemId,
            Long jobId) {
    }
}
