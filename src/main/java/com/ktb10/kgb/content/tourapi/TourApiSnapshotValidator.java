package com.ktb10.kgb.content.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** TourAPI 목록 응답의 성공 코드, 전체 건수와 contentid 중복을 검증합니다. */
@Component
public class TourApiSnapshotValidator {

    private final ObjectMapper objectMapper;
    private final TourApiProperties properties;

    public TourApiSnapshotValidator(ObjectMapper objectMapper, TourApiProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ValidatedSnapshot validate(SnapshotType type, byte[] raw) {
        if (raw.length > properties.maxResponseBytes()) {
            throw new TourApiException(type.label() + " 응답 크기가 제한을 초과했습니다.", false);
        }
        try {
            JsonNode response = objectMapper.readTree(raw).path("response");
            String resultCode = response.path("header").path("resultCode").asText();
            if (!"0000".equals(resultCode)) {
                throw new TourApiException(
                        type.label() + " resultCode가 정상이 아닙니다: " + resultCode,
                        false);
            }
            JsonNode body = response.path("body");
            int totalCount = body.path("totalCount").asInt(-1);
            JsonNode items = body.path("items").path("item");
            if (totalCount < 0 || !items.isArray()) {
                throw incomplete(type, "목록 응답 구조가 올바르지 않습니다.");
            }
            Set<String> contentIds = new HashSet<>();
            for (JsonNode item : items) {
                String contentId = item.path("contentid").asText("").trim();
                if (contentId.isEmpty()) {
                    throw incomplete(type, "contentid가 없는 항목이 있습니다.");
                }
                if (!contentIds.add(contentId)) {
                    throw incomplete(type, "중복 contentid가 있습니다: " + contentId);
                }
            }
            if (contentIds.size() != totalCount) {
                throw incomplete(type, "전체 건수와 고유 항목 수가 다릅니다. totalCount="
                        + totalCount + ", items=" + contentIds.size());
            }
            return new ValidatedSnapshot(type, raw, totalCount);
        } catch (TourApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new TourApiException(type.label() + " JSON을 파싱할 수 없습니다.", true, exception);
        }
    }

    private TourApiException incomplete(SnapshotType type, String message) {
        return new TourApiException(type.label() + " " + message, true);
    }

    public enum SnapshotType {
        AREA("TourAPI 전국 목록"),
        FESTIVAL("TourAPI 행사 목록");

        private final String label;

        SnapshotType(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    public record ValidatedSnapshot(SnapshotType type, byte[] raw, int totalCount) {
    }
}
