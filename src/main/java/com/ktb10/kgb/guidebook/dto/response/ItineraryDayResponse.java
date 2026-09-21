package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record ItineraryDayResponse(
        @JsonProperty("day_number")
        Short dayNumber,

        @JsonProperty("itinerary_date")
        LocalDate itineraryDate,

        List<ItineraryItemResponse> items) {
}
