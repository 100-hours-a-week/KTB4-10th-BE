package com.ktb10.kgb.guidebook.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** AI 서버에 전달하는 가이드북 생성 요청입니다. */
public record AiGuidebookRequest(
        @JsonProperty("request_id")
        String requestId,
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

    public record Region(String province, String city) {
    }

    public record Content(
            @JsonProperty("content_id")
            String contentId,
            @JsonProperty("content_type")
            ContentType contentType,
            String title,
            String category,
            @JsonProperty("classification_code_1")
            String classificationCode1,
            @JsonProperty("classification_code_2")
            String classificationCode2,
            @JsonProperty("classification_code_3")
            String classificationCode3,
            String address,
            Coordinates coordinates,
            @JsonProperty("image_url")
            String imageUrl,
            @JsonProperty("event_period")
            EventPeriod eventPeriod) {
    }

    public enum ContentType {
        PLACE,
        EVENT
    }

    public record Coordinates(double lat, double lng) {
    }

    public record EventPeriod(
            @JsonProperty("start_date")
            @JsonFormat(pattern = "yy.MM.dd")
            LocalDate startDate,
            @JsonProperty("end_date")
            @JsonFormat(pattern = "yy.MM.dd")
            LocalDate endDate) {
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
