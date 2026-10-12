package com.ktb10.kgb.content.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.content.tourapi.TourApiSnapshotValidator.SnapshotType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TourApiSnapshotValidatorTest {

    private final TourApiSnapshotValidator validator = new TourApiSnapshotValidator(
            new ObjectMapper(),
            TourApiTestFixtures.properties("data/tourism/raw"));

    @Test
    void acceptsCompleteAreaAndFestivalSnapshots() {
        byte[] raw = snapshot(2, "100", "200");

        assertThat(validator.validate(SnapshotType.AREA, raw).totalCount()).isEqualTo(2);
        assertThat(validator.validate(SnapshotType.FESTIVAL, raw).totalCount()).isEqualTo(2);
    }

    @Test
    void rejectsCountMismatchAndDuplicateContentId() {
        assertThatThrownBy(() -> validator.validate(
                SnapshotType.AREA,
                snapshot(3, "100", "200")))
                .isInstanceOf(TourApiException.class)
                .hasMessageContaining("전체 건수와 고유 항목 수");
        assertThatThrownBy(() -> validator.validate(
                SnapshotType.FESTIVAL,
                snapshot(2, "100", "100")))
                .isInstanceOf(TourApiException.class)
                .hasMessageContaining("중복 contentid");
    }

    private byte[] snapshot(int totalCount, String... contentIds) {
        StringBuilder items = new StringBuilder();
        for (String contentId : contentIds) {
            if (!items.isEmpty()) {
                items.append(',');
            }
            items.append("{\"contentid\":\"").append(contentId).append("\"}");
        }
        return ("{\"response\":{\"header\":{\"resultCode\":\"0000\"},"
                + "\"body\":{\"totalCount\":" + totalCount
                + ",\"items\":{\"item\":[" + items + "]}}}}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
