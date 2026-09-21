package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import java.time.LocalDate;
import java.util.List;

public record GuidebookDetailResponse(
        @JsonProperty("guidebook_id")
        Long guidebookId,

        String title,

        @JsonProperty("start_date")
        LocalDate startDate,

        @JsonProperty("end_date")
        LocalDate endDate,

        @JsonProperty("people_count")
        Integer peopleCount,

        @JsonProperty("itinerary_summary")
        List<ItineraryDaySummaryResponse> itinerarySummary) {

    public static GuidebookDetailResponse from(
            Guidebook guidebook,
            List<ItineraryDaySummaryResponse> itinerarySummary) {
        return new GuidebookDetailResponse(
                guidebook.getId(),
                guidebook.getTitle(),
                guidebook.getStartDate(),
                guidebook.getEndDate(),
                guidebook.getPeopleCount(),
                List.copyOf(itinerarySummary));
    }
}
