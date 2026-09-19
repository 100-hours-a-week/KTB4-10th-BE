package com.ktb10.kgb.guidebook.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.entity.Companion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GuidebookGenerationRequest(
        @JsonProperty("region_code")
        @NotBlank
        @Size(max = 20)
        String regionCode,

        @JsonProperty("start_date")
        @NotNull
        LocalDate startDate,

        @JsonProperty("end_date")
        @NotNull
        LocalDate endDate,

        @NotNull
        Companion companion,

        @JsonProperty("people_count")
        @NotNull
        @Min(1)
        @Max(10)
        Integer peopleCount) {
}
