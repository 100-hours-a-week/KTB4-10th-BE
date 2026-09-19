package com.ktb10.kgb.guidebook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guidebooks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guidebook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 15, nullable = false)
    private String title;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "companion", length = 20, nullable = false)
    private Companion companion;

    @Column(name = "people_count", nullable = false)
    private Integer peopleCount;

    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Guidebook create(
            String title,
            Long regionId,
            LocalDate startDate,
            LocalDate endDate,
            Companion companion,
            int peopleCount,
            String contentHtml,
            LocalDateTime now) {
        validatePeriod(startDate, endDate);
        if (peopleCount < 1) {
            throw new IllegalArgumentException("여행 인원은 1명 이상이어야 합니다.");
        }

        Guidebook guidebook = new Guidebook();
        guidebook.title = Objects.requireNonNull(title);
        guidebook.regionId = Objects.requireNonNull(regionId);
        guidebook.startDate = startDate;
        guidebook.endDate = endDate;
        guidebook.companion = Objects.requireNonNull(companion);
        guidebook.peopleCount = peopleCount;
        guidebook.contentHtml = contentHtml;
        guidebook.version = 1;
        guidebook.createdAt = Objects.requireNonNull(now);
        guidebook.updatedAt = now;
        return guidebook;
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate);
        Objects.requireNonNull(endDate);
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("여행 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

}
