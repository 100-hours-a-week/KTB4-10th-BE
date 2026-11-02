package com.ktb10.kgb.guidebook.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import com.ktb10.kgb.member.entity.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RegenerationInputSnapshotTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ItineraryDayRepository days = mock(ItineraryDayRepository.class);
    private final ItineraryItemRepository items = mock(ItineraryItemRepository.class);
    private final RegenerationInputSnapshot snapshots = new RegenerationInputSnapshot(days, items, json);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Test
    void preservesCandidatesAndReplacesPreviousPlanWithCurrentStoredItinerary() throws Exception {
        Guidebook book = Guidebook.create("기존 제목", 1L, LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 10), Companion.COUPLE, 2, "html", now);
        ReflectionTestUtils.setField(book, "id", 50L);
        GenerationJob source = GenerationJob.createInitial(mock(Member.class), "{}", "key", now);
        ReflectionTestUtils.setField(source, "id", 123L);
        String snapshot = """
                {"schemaVersion":1,"input":{"region":{"city":"중구"},
                "contents":[{"content_id":"100","overview":"원본 후보"}],
                "feedback":"이전 피드백","previous_plan":[{"content_id":"old"}]}}
                """;
        ReflectionTestUtils.setField(source, "messagePayload", snapshot);
        ItineraryDay day = ItineraryDay.create(book, 1, book.getStartDate());
        ReflectionTestUtils.setField(day, "id", 10L);
        ItineraryItem item = ItineraryItem.create(day, 1L, 1, LocalTime.of(9, 30),
                "{\"content_id\":\"100\",\"duration_minutes\":60}", now);
        when(days.findAllByGuidebookIdOrderByDayNumberAsc(50L)).thenReturn(List.of(day));
        when(items.findAllByItineraryDayIdInOrderByItineraryDayDayNumberAscSequenceAsc(any()))
                .thenReturn(List.of(item));

        var input = snapshots.build(source, book, "걷기 줄이기");

        assertThat(input.path("contents").get(0).path("overview").asText()).isEqualTo("원본 후보");
        assertThat(input.path("previous_plan").size()).isEqualTo(1);
        assertThat(input.path("previous_plan").get(0).path("content_id").asText()).isEqualTo("100");
        assertThat(input.path("previous_plan").get(0).path("start_time").asText()).isEqualTo("09:30");
        assertThat(input.path("feedback").asText()).isEqualTo("걷기 줄이기");
        assertThat(input.path("title").asText()).isEqualTo("기존 제목");
        assertThat(input.path("source_job_id").asLong()).isEqualTo(123L);
        assertThat(source.getMessagePayload()).isEqualTo(snapshot);

        var mapper = mock(com.ktb10.kgb.guidebook.client.AiGuidebookRequestMapper.class);
        var preparation = new GenerationMessagePreparation(mapper, json, java.time.Clock.systemUTC(), snapshots);
        GenerationJob pending = GenerationJob.createRegeneration(mock(Member.class), 50L,
                "{\"request\":{\"feedback\":\"새 피드백\"}}", "regen", now);
        preparation.prepareRegeneration(pending, source, book);
        assertThat(pending.getId()).isNull();
        assertThat(pending.getStatus()).isEqualTo(
                com.ktb10.kgb.guidebook.entity.GenerationStatus.PUBLISH_PENDING);
        ReflectionTestUtils.setField(pending, "id", 124L);
        var envelope = json.readTree(preparation.buildMessage(pending));
        assertThat(envelope.path("messageId").asText()).isEqualTo("124-0");
        assertThat(envelope.path("input").path("source_job_id").asLong()).isEqualTo(123L);
        assertThat(envelope.path("input").path("feedback").asText()).isEqualTo("새 피드백");
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsLegacySourceWithoutCandidateSnapshot() {
        GenerationJob source = GenerationJob.createInitial(mock(Member.class), "{}", "key", now);
        assertThatThrownBy(() -> snapshots.build(source, mock(Guidebook.class), "피드백"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(days, items);
    }
}
