package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ItineraryDaySummaryResponse(
        @JsonProperty("day_number")
        Short dayNumber,

        @JsonProperty("first_place_name")
        String firstPlaceName,

        @JsonProperty("remaining_place_count")
        int remainingPlaceCount) {
}
