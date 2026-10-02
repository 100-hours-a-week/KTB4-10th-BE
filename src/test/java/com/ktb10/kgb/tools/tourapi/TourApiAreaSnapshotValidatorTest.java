package com.ktb10.kgb.tools.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TourApiAreaSnapshotValidatorTest {

    private TourApiAreaSnapshotValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TourApiAreaSnapshotValidator(
                new ObjectMapper(),
                properties("data/tourism/raw"));
    }

    @Test
    void acceptsCompleteSnapshot() {
        byte[] raw = snapshot(2, "100", "200");

        var result = validator.validate(raw);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.responseRows()).isEqualTo(60000);
        assertThat(result.raw()).isSameAs(raw);
    }

    @Test
    void rejectsSnapshotWhenTotalCountAndUniqueItemsDiffer() {
        byte[] raw = snapshot(3, "100", "200");

        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(TourApiCollectionException.class)
                .hasMessageContaining("전체 건수와 고유 항목 수")
                .extracting(exception -> ((TourApiCollectionException) exception).isRetryable())
                .isEqualTo(true);
    }

    @Test
    void rejectsDuplicateContentId() {
        byte[] raw = snapshot(2, "100", "100");

        assertThatThrownBy(() -> validator.validate(raw))
                .isInstanceOf(TourApiCollectionException.class)
                .hasMessageContaining("중복 contentid");
    }

    private byte[] snapshot(int totalCount, String... contentIds) {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < contentIds.length; index++) {
            if (index > 0) {
                items.append(',');
            }
            items.append("{\"contentid\":\"")
                    .append(contentIds[index])
                    .append("\"}");
        }
        return ("""
                {
                  "response": {
                    "header": {"resultCode": "0000"},
                    "body": {
                      "totalCount": %d,
                      "numOfRows": 60000,
                      "items": {"item": [%s]}
                    }
                  }
                }
                """).formatted(totalCount, items).getBytes(StandardCharsets.UTF_8);
    }

    static TourApiCollectionProperties properties(String snapshotLocation) {
        return new TourApiCollectionProperties(
                URI.create("https://apis.data.go.kr/B551011/KorService2"),
                "service-key",
                snapshotLocation,
                "ETC",
                "KGB",
                60000,
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                64 * 1024 * 1024,
                3);
    }
}
