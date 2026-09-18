package com.ktb10.kgb.guidebook.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class GuidebookEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 12, 0);

    @Test
    void createsItineraryDayWithinGuidebookPeriod() {
        Guidebook guidebook = guidebook();

        ItineraryDay day = ItineraryDay.create(
                guidebook,
                2,
                LocalDate.of(2026, 10, 2));

        assertThat(day.getGuidebook()).isSameAs(guidebook);
        assertThat(day.getDayNumber()).isEqualTo((short) 2);
        assertThat(day.getItineraryDate()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void rejectsItineraryDayOutsideGuidebookPeriod() {
        assertThatThrownBy(() -> ItineraryDay.create(
                        guidebook(),
                        4,
                        LocalDate.of(2026, 10, 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("여행 기간");
    }

    @Test
    void rejectsItineraryDayNumberOverMaximumTripDuration() {
        assertThatThrownBy(() -> ItineraryDay.create(
                        guidebook(),
                        8,
                        LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상 7 이하");
    }

    @Test
    void createsItineraryItemWithOptionalContentAndTime() {
        ItineraryDay day = ItineraryDay.create(
                guidebook(),
                1,
                LocalDate.of(2026, 10, 1));

        ItineraryItem item = ItineraryItem.create(
                day,
                null,
                1,
                LocalTime.of(9, 30),
                "{\"title\":\"아침 산책\"}",
                NOW);

        assertThat(item.getItineraryDay()).isSameAs(day);
        assertThat(item.getTourismContentId()).isNull();
        assertThat(item.getSequence()).isEqualTo((short) 1);
        assertThat(item.getScheduledTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void rejectsBlankPlaceSnapshotAndInvalidSequence() {
        ItineraryDay day = ItineraryDay.create(
                guidebook(),
                1,
                LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> ItineraryItem.create(
                        day,
                        1L,
                        0,
                        null,
                        "{}",
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItineraryItem.create(
                        day,
                        1L,
                        1,
                        null,
                        " ",
                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("장소 스냅샷");
    }

    @Test
    void rejectsItineraryItemSequenceOverDailyMaximum() {
        ItineraryDay day = ItineraryDay.create(
                guidebook(),
                1,
                LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> ItineraryItem.create(
                        day,
                        1L,
                        21,
                        null,
                        "{}",
                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상 20 이하");
    }

    private static Guidebook guidebook() {
        return Guidebook.create(
                "서울 여행",
                1L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 3),
                Companion.FRIEND,
                2,
                "<p>여행</p>",
                NOW);
    }
}
