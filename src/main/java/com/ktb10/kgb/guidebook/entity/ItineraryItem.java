package com.ktb10.kgb.guidebook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "itinerary_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_itinerary_items_sequence",
                columnNames = {"itinerary_day_id", "sequence"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryItem {

    private static final int MAX_SEQUENCE = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "itinerary_day_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_itinerary_items_day"))
    private ItineraryDay itineraryDay;

    @Column(name = "tourism_content_id")
    private Long tourismContentId;

    @Column(name = "sequence", nullable = false)
    private Short sequence;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Column(name = "place_snapshot", nullable = false, columnDefinition = "json")
    private String placeSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ItineraryItem create(
            ItineraryDay itineraryDay,
            Long tourismContentId,
            int sequence,
            LocalTime scheduledTime,
            String placeSnapshot,
            LocalDateTime createdAt) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("일정 방문 순서는 1 이상 20 이하여야 합니다.");
        }

        ItineraryItem itineraryItem = new ItineraryItem();
        itineraryItem.itineraryDay = Objects.requireNonNull(itineraryDay);
        itineraryItem.tourismContentId = tourismContentId;
        itineraryItem.sequence = (short) sequence;
        itineraryItem.scheduledTime = scheduledTime;
        itineraryItem.placeSnapshot = requireText(placeSnapshot, "장소 스냅샷");
        itineraryItem.createdAt = Objects.requireNonNull(createdAt);
        return itineraryItem;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값은 비어 있을 수 없습니다.");
        }
        return value;
    }

}
