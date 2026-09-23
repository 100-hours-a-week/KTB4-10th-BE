package com.ktb10.kgb.guidebook.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ktb10.kgb.guidebook.client.AiGenerationStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** AI 작업 상태와 완료 결과를 표현합니다. */
public record AiGenerationStatusResponse(
        @JsonProperty("job_id")
        String jobId,
        AiGenerationStatus status,
        GuidebookResult result,
        GenerationError error) {

    public static AiGenerationStatusResponse processing(String jobId) {
        return new AiGenerationStatusResponse(
                jobId,
                AiGenerationStatus.PROCESSING,
                null,
                null);
    }

    public static AiGenerationStatusResponse completed(
            String jobId,
            GuidebookResult result) {
        return new AiGenerationStatusResponse(
                jobId,
                AiGenerationStatus.COMPLETED,
                result,
                null);
    }

    public record GuidebookResult(
            String region,
            String title,
            String summary,
            List<ItineraryDay> itinerary) {

        public GuidebookResult {
            itinerary = List.copyOf(itinerary);
        }
    }

    public record ItineraryDay(
            int day,
            LocalDate date,
            List<Place> places) {

        public ItineraryDay {
            places = List.copyOf(places);
        }
    }

    public record Place(
            int order,
            LocalTime time,
            @JsonProperty("content_id")
            String contentId,
            String name,
            String category,
            String description,
            @JsonProperty("recommend_reason")
            String recommendReason,
            String tip,
            @JsonProperty("duration_minutes")
            Integer durationMinutes,
            String address,
            Coordinates coordinates,
            @JsonProperty("image_url")
            String imageUrl) {
    }

    public record Coordinates(double lat, double lng) {
    }

    public record GenerationError(String code, String message) {
    }
}
