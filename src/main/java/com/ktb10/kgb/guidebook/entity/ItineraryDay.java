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
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "itinerary_days",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_itinerary_days_number",
                    columnNames = {"guidebook_id", "day_number"}),
            @UniqueConstraint(
                    name = "uq_itinerary_days_date",
                    columnNames = {"guidebook_id", "itinerary_date"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryDay {

    private static final int MAX_DAY_NUMBER = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "guidebook_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_itinerary_days_guidebook"))
    private Guidebook guidebook;

    @Column(name = "day_number", nullable = false)
    private Short dayNumber;

    @Column(name = "itinerary_date", nullable = false)
    private LocalDate itineraryDate;

    public static ItineraryDay create(
            Guidebook guidebook, int dayNumber, LocalDate itineraryDate) {
        if (dayNumber < 1 || dayNumber > MAX_DAY_NUMBER) {
            throw new IllegalArgumentException("일정 일차는 1 이상 7 이하여야 합니다.");
        }

        Objects.requireNonNull(guidebook);
        Objects.requireNonNull(itineraryDate);
        if (itineraryDate.isBefore(guidebook.getStartDate())
                || itineraryDate.isAfter(guidebook.getEndDate())) {
            throw new IllegalArgumentException("일정 날짜는 가이드북 여행 기간 안에 있어야 합니다.");
        }

        ItineraryDay itineraryDay = new ItineraryDay();
        itineraryDay.guidebook = guidebook;
        itineraryDay.dayNumber = (short) dayNumber;
        itineraryDay.itineraryDate = itineraryDate;
        return itineraryDay;
    }

}
