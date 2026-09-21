package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record GuidebookListItemResponse(
        @JsonProperty("guidebook_id")
        Long guidebookId,

        String title,

        @JsonProperty("start_date")
        LocalDate startDate,

        @JsonProperty("end_date")
        LocalDate endDate,

        Companion companion,

        @JsonProperty("people_count")
        Integer peopleCount,

        Integer version,

        @JsonProperty("updated_at")
        OffsetDateTime updatedAt) {

    public static GuidebookListItemResponse from(Guidebook guidebook) {
        return new GuidebookListItemResponse(
                guidebook.getId(),
                guidebook.getTitle(),
                guidebook.getStartDate(),
                guidebook.getEndDate(),
                guidebook.getCompanion(),
                guidebook.getPeopleCount(),
                guidebook.getVersion(),
                guidebook.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }
}
