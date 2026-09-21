package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import java.time.LocalTime;

public record ItineraryItemResponse(
        @JsonProperty("item_id")
        Long itemId,

        @JsonProperty("content_id")
        Long contentId,

        Short sequence,

        @JsonProperty("scheduled_time")
        LocalTime scheduledTime,

        @JsonProperty("place_snapshot")
        JsonNode placeSnapshot) {

    public static ItineraryItemResponse from(
            ItineraryItem item,
            JsonNode placeSnapshot) {
        return new ItineraryItemResponse(
                item.getId(),
                item.getTourismContentId(),
                item.getSequence(),
                item.getScheduledTime(),
                placeSnapshot);
    }
}
