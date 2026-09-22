package com.ktb10.kgb.guidebook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.guidebook.dto.response.GuidebookDetailResponse;
import com.ktb10.kgb.guidebook.dto.response.ItineraryDayResponse;
import com.ktb10.kgb.guidebook.dto.response.ItineraryItemResponse;
import com.ktb10.kgb.guidebook.entity.Guidebook;
import com.ktb10.kgb.guidebook.entity.ItineraryDay;
import com.ktb10.kgb.guidebook.entity.ItineraryItem;
import com.ktb10.kgb.guidebook.repository.ItineraryDayRepository;
import com.ktb10.kgb.guidebook.repository.ItineraryItemRepository;
import com.ktb10.kgb.guidebook.repository.MemberGuidebookRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가이드북 조회, 일정, 삭제 등 일반적인 비즈니스 로직을 담당합니다. */
@Service
public class GuidebookService {

    private final MemberGuidebookRepository memberGuidebookRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final ObjectMapper objectMapper;

    public GuidebookService(
            MemberGuidebookRepository memberGuidebookRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            ObjectMapper objectMapper) {
        this.memberGuidebookRepository = memberGuidebookRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GuidebookDetailResponse getGuidebookDetail(Long memberId, Long guidebookId) {
        Guidebook guidebook = memberGuidebookRepository
                .findActiveWithGuidebook(memberId, guidebookId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
                .getGuidebook();

        List<ItineraryDay> days = itineraryDayRepository
                .findAllByGuidebookIdOrderByDayNumberAsc(guidebookId);
        List<Long> dayIds = days.stream().map(ItineraryDay::getId).toList();
        Map<Long, List<ItineraryItem>> itemsByDayId = dayIds.isEmpty()
                ? Map.of()
                : itineraryItemRepository
                        .findAllByItineraryDayIdInOrderByItineraryDayDayNumberAscSequenceAsc(dayIds)
                        .stream()
                        .collect(Collectors.groupingBy(item -> item.getItineraryDay().getId()));

        List<ItineraryDayResponse> itinerary = days.stream()
                .map(day -> toItineraryDay(
                        day, itemsByDayId.getOrDefault(day.getId(), List.of())))
                .toList();
        return GuidebookDetailResponse.from(guidebook, itinerary);
    }

    private ItineraryDayResponse toItineraryDay(
            ItineraryDay day,
            List<ItineraryItem> items) {
        List<ItineraryItemResponse> itemResponses = items.stream()
                .map(item -> ItineraryItemResponse.from(
                        item, parsePlaceSnapshot(item.getPlaceSnapshot())))
                .toList();
        return new ItineraryDayResponse(
                day.getDayNumber(),
                day.getItineraryDate(),
                itemResponses);
    }

    private JsonNode parsePlaceSnapshot(String placeSnapshot) {
        try {
            JsonNode snapshot = objectMapper.readTree(placeSnapshot);
            if (snapshot.isTextual()) {
                snapshot = objectMapper.readTree(snapshot.asText());
            }
            return snapshot;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }
}
