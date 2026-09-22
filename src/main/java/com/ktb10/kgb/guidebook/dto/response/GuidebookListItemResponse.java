package com.ktb10.kgb.guidebook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.Companion;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import java.time.LocalDate;

public record GuidebookListItemResponse(
        @JsonProperty("guidebook_id")
        Long guidebookId,

        String title,

        @JsonProperty("start_date")
        LocalDate startDate,

        @JsonProperty("end_date")
        LocalDate endDate,

        Companion companion) {

    public static GuidebookListItemResponse from(Guidebook guidebook) {
        return new GuidebookListItemResponse(
                guidebook.getId(),
                guidebook.getTitle(),
                guidebook.getStartDate(),
                guidebook.getEndDate(),
                guidebook.getCompanion());
    }
}
