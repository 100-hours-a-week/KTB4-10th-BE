package com.ktb10.kgb.member.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.member.entity.PreferenceCode;
import com.ktb10.kgb.member.entity.PreferenceType;
import java.util.List;

/** V1에서 선택 가능한 전체 취향 옵션 응답입니다. */
public record PreferenceOptionResponse(List<Item> items) {

    public PreferenceOptionResponse {
        items = List.copyOf(items);
    }

    /** 취향 옵션 하나의 안정 코드와 표시 메타데이터입니다. */
    public record Item(
            @JsonProperty("preference_type") PreferenceType preferenceType,
            String code,
            String label,
            @JsonProperty("parent_code") String parentCode,
            @JsonProperty("sort_order") int sortOrder) {

        public static Item from(PreferenceCode preferenceCode) {
            return new Item(
                    preferenceCode.type(),
                    preferenceCode.name(),
                    preferenceCode.label(),
                    preferenceCode.parentCode(),
                    preferenceCode.sortOrder());
        }
    }
}
