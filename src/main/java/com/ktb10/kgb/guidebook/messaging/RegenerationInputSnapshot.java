package com.ktb10.kgb.guidebook.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.entity.GenerationJob;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** 재생성 원본을 DB 스냅샷에서 구성하며 관광 후보를 다시 조회하지 않습니다. */
@Component
public class RegenerationInputSnapshot {

    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;
    private final ObjectMapper json;

    public RegenerationInputSnapshot(ItineraryDayRepository days, ItineraryItemRepository items, ObjectMapper json) {
        this.days = days;
        this.items = items;
        this.json = json;
    }

    public ObjectNode build(GenerationJob source, Guidebook guidebook, String feedback) {
        if (source.getMessagePayload() == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
        }
        try {
            var snapshot = json.readTree(source.getMessagePayload());
            if (snapshot.path("schemaVersion").asInt() != 1 || !snapshot.path("input").isObject()
                    || !snapshot.path("input").path("contents").isArray()) {
                throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
            }
            ObjectNode input = ((ObjectNode) snapshot.get("input")).deepCopy();
            input.remove("request_id");
            input.put("source_job_id", source.getId());
            input.put("source_guidebook_version", guidebook.getVersion());
            input.put("title", guidebook.getTitle());
            input.put("feedback", feedback);
            List<ItineraryDay> storedDays = days.findAllByGuidebookIdOrderByDayNumberAsc(guidebook.getId());
            if (storedDays.isEmpty()) {
                throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
            }
            List<ItineraryItem> storedItems = items
                    .findAllByItineraryDayIdInOrderByItineraryDayDayNumberAscSequenceAsc(
                            storedDays.stream().map(ItineraryDay::getId).toList());
            if (storedItems.isEmpty()) {
                throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
            }
            ArrayNode plan = input.putArray("previous_plan");
            for (ItineraryItem item : storedItems) {
                var place = json.readTree(item.getPlaceSnapshot());
                if (!place.path("content_id").isTextual()
                        || !place.path("duration_minutes").isIntegralNumber()
                        || item.getScheduledTime() == null) {
                    throw new BusinessException(CommonErrorCode.RESOURCE_STATE_CONFLICT);
                }
                ObjectNode entry = plan.addObject();
                entry.put("content_id", place.get("content_id").asText());
                entry.put("day", item.getItineraryDay().getDayNumber());
                entry.put("order", item.getSequence());
                entry.put("start_time", item.getScheduledTime().toString());
                entry.put("duration_minutes", place.get("duration_minutes").asInt());
            }
            return input;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("재생성 원본 스냅샷을 읽을 수 없습니다.", exception);
        }
    }
}
