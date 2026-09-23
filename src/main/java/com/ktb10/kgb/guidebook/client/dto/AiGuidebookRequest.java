package com.ktb10.kgb.guidebook.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** AI 서버에 전달하는 가이드북 생성 요청입니다. */
public record AiGuidebookRequest(
        Region region,
        @JsonProperty("start_date")
        @JsonFormat(pattern = "yy.MM.dd")
        LocalDate startDate,
        @JsonProperty("end_date")
        @JsonFormat(pattern = "yy.MM.dd")
        LocalDate endDate,
        String companion,
        @JsonProperty("people_count")
        Integer peopleCount,
        Preferences preferences) {

    public record Region(String province, String city) {
    }

    public record Preferences(
            @JsonProperty("large_category")
            List<String> largeCategory,
            @JsonProperty("mid_category")
            Map<String, List<String>> midCategory,
            @JsonProperty("travel_style")
            List<String> travelStyle) {

        public Preferences {
            largeCategory = List.copyOf(largeCategory);
            midCategory = midCategory.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            Map.Entry::getKey,
                            entry -> List.copyOf(entry.getValue())));
            travelStyle = List.copyOf(travelStyle);
        }
    }
}
