package com.ktb10.kgb.member.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원이 저장할 하나의 취향 유형과 코드입니다. */
public record PreferenceSelectionRequest(
        @JsonProperty("preference_type")
        @NotBlank
        @Size(max = 20)
        String preferenceType,

        @JsonProperty("preference_code")
        @NotBlank
        @Size(max = 50)
        String preferenceCode) {
}
