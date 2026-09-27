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
        Preferences preferences,
        List<Content> contents) {

    public AiGuidebookRequest {
        contents = List.copyOf(contents);
    }

    public AiGuidebookRequest(
            Region region,
            LocalDate startDate,
            LocalDate endDate,
            String companion,
            Integer peopleCount,
            Preferences preferences) {
        this(region, startDate, endDate, companion, peopleCount, preferences, List.of());
    }

    public record Region(String province, String city) {
    }

    public record Content(
            @JsonProperty("contentid")
            String contentId,
            @JsonProperty("contenttypeid")
            String contentTypeId,
            String title,
            @JsonProperty("lclsSystm1")
            String lclsSystm1,
            @JsonProperty("lclsSystm2")
            String lclsSystm2,
            @JsonProperty("lclsSystm3")
            String lclsSystm3,
            @JsonProperty("addr1")
            String address,
            @JsonProperty("mapx")
            double mapx,
            @JsonProperty("mapy")
            double mapy,
            @JsonProperty("firstimage")
            String firstImage,
            @JsonProperty("eventstartdate")
            @JsonFormat(pattern = "yyyyMMdd")
            LocalDate eventStartDate,
            @JsonProperty("eventenddate")
            @JsonFormat(pattern = "yyyyMMdd")
            LocalDate eventEndDate) {
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
