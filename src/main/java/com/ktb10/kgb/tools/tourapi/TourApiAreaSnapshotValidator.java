package com.ktb10.kgb.tools.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** TourAPI areaBasedList2 응답이 전국 목록 전체인지 검증합니다. */
@Component
public class TourApiAreaSnapshotValidator {

    private final ObjectMapper objectMapper;
    private final TourApiCollectionProperties properties;

    public TourApiAreaSnapshotValidator(
            ObjectMapper objectMapper,
            TourApiCollectionProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ValidatedAreaSnapshot validate(byte[] raw) {
        if (raw.length > properties.maxResponseBytes()) {
            throw new TourApiCollectionException(
                    "TourAPI 응답 크기가 제한을 초과했습니다: " + raw.length,
                    false);
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode response = root.path("response");
            String resultCode = response.path("header").path("resultCode").asText();
            if (!"0000".equals(resultCode)) {
                throw new TourApiCollectionException(
                        "TourAPI resultCode가 정상이 아닙니다: " + resultCode,
                        false);
            }

            JsonNode body = response.path("body");
            int totalCount = body.path("totalCount").asInt(-1);
            int responseRows = body.path("numOfRows").asInt(-1);
            JsonNode items = body.path("items").path("item");
            if (totalCount < 0 || responseRows < 0 || !items.isArray()) {
                throw incomplete("TourAPI 목록 응답 구조가 올바르지 않습니다.");
            }

            Set<String> contentIds = new HashSet<>();
            for (JsonNode item : items) {
                String contentId = item.path("contentid").asText("").trim();
                if (contentId.isEmpty()) {
                    throw incomplete("contentid가 없는 항목이 있습니다.");
                }
                if (!contentIds.add(contentId)) {
                    throw incomplete("중복 contentid가 있습니다: " + contentId);
                }
            }
            if (contentIds.size() != totalCount) {
                throw incomplete(
                        "TourAPI 전체 건수와 고유 항목 수가 다릅니다. totalCount="
                                + totalCount
                                + ", items="
                                + contentIds.size());
            }
            return new ValidatedAreaSnapshot(raw, totalCount, responseRows);
        } catch (TourApiCollectionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new TourApiCollectionException(
                    "TourAPI JSON 응답을 파싱할 수 없습니다.",
                    true,
                    exception);
        }
    }

    private TourApiCollectionException incomplete(String message) {
        return new TourApiCollectionException(message, true);
    }

    public record ValidatedAreaSnapshot(
            byte[] raw,
            int totalCount,
            int responseRows) {
    }
}
