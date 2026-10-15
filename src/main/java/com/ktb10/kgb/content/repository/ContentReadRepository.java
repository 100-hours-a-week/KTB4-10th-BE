package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.entity.TourismContentReadEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 정보 탭 행사 목록과 상세를 projection으로 조회합니다. */
public interface ContentReadRepository extends JpaRepository<TourismContentReadEntity, Long> {

    @Query(
            value =
                    """
                    SELECT content.source_content_id AS contentId,
                           content.title AS title,
                           region.administrative_code AS regionCode,
                           region.name AS regionName,
                           content.address AS address,
                           content.thumbnail_url AS thumbnailUrl,
                           event.start_date AS startDate,
                           event.end_date AS endDate
                    FROM tourism_contents content
                    JOIN regions region ON region.id = content.region_id
                    JOIN event_details event ON event.content_id = content.id
                    WHERE content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 = 'EV'
                      AND (:query IS NULL OR LOCATE(:query, content.title) > 0)
                    ORDER BY content.created_at DESC, content.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(*)
                    FROM tourism_contents content
                    JOIN event_details event ON event.content_id = content.id
                    WHERE content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 = 'EV'
                      AND (:query IS NULL OR LOCATE(:query, content.title) > 0)
                    """,
            nativeQuery = true)
    Page<ContentListProjection> findEvents(
            @Param("query") String query,
            Pageable pageable);

    @Query(
            value =
                    """
                    SELECT content.id AS id,
                           content.source_content_id AS contentId,
                           content.title AS title,
                           region.administrative_code AS regionCode,
                           region.name AS regionName,
                           ST_Latitude(content.location) AS latitude,
                           ST_Longitude(content.location) AS longitude,
                           content.thumbnail_url AS thumbnailUrl,
                           content.description AS description,
                           content.address AS address,
                           content.phone AS phone,
                           content.homepage_url AS homepageUrl,
                           event.start_date AS startDate,
                           event.end_date AS endDate,
                           event.operating_hours AS operatingHours,
                           event.organizer AS organizer
                    FROM tourism_contents content
                    JOIN regions region ON region.id = content.region_id
                    JOIN event_details event ON event.content_id = content.id
                    WHERE content.source_content_id = :contentId
                      AND content.status = 'ACTIVE'
                      AND content.deleted_at IS NULL
                      AND content.classification_code_1 = 'EV'
                    """,
            nativeQuery = true)
    Optional<ContentDetailProjection> findEventDetail(@Param("contentId") String contentId);

    @Query(
            value =
                    """
                    SELECT CAST(id AS CHAR) AS imageId,
                           image_url AS imageUrl,
                           sort_order AS sortOrder
                    FROM content_images
                    WHERE content_id = :contentId
                    ORDER BY sort_order, id
                    """,
            nativeQuery = true)
    List<ContentImageProjection> findImages(@Param("contentId") long contentId);

    interface ContentListProjection {
        String getContentId();

        String getTitle();

        String getRegionCode();

        String getRegionName();

        String getAddress();

        String getThumbnailUrl();

        LocalDate getStartDate();

        LocalDate getEndDate();
    }

    interface ContentDetailProjection extends ContentListProjection {
        long getId();

        double getLatitude();

        double getLongitude();

        String getDescription();

        String getPhone();

        String getHomepageUrl();

        String getOperatingHours();

        String getOrganizer();
    }

    interface ContentImageProjection {
        String getImageId();

        String getImageUrl();

        int getSortOrder();
    }
}
