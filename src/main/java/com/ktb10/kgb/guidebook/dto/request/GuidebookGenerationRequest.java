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
        @NotBlank
        @Size(max = 20)
        String province,

        @NotBlank
        @Size(max = 20)
        String city,

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
        Integer peopleCount,

        @JsonProperty("user_request")
        // null은 선택 입력으로 허용하고, trim 후 빈 값은 재생성의 @NotBlank와 동일하게 거절합니다.
        @Size(min = 1, max = 200)
        String userRequest) {

    public GuidebookGenerationRequest {
        userRequest = userRequest == null ? null : userRequest.trim();
    }

    public GuidebookGenerationRequest(String province, String city, LocalDate startDate,
            LocalDate endDate, Companion companion, Integer peopleCount) {
        this(province, city, startDate, endDate, companion, peopleCount, null);
    }
}
