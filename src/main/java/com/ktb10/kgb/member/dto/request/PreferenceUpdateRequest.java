package com.ktb10.kgb.member.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 현재 회원 취향을 전체 교체하기 위한 요청입니다. */
public record PreferenceUpdateRequest(
        @NotNull
        List<@NotNull @Valid PreferenceSelectionRequest> selections) {
}
