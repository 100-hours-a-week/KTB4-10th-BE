package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.error.ErrorDetail;
import com.ktb10.kgb.common.observability.MapPerformanceMetrics;
import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentResponse;
import com.ktb10.kgb.content.repository.MapClusterQueryResult;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import com.ktb10.kgb.content.repository.MapContentPersonalization;
import com.ktb10.kgb.content.repository.MapContentPersonalizationQuery;
import com.ktb10.kgb.content.repository.MapContentQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 클라이언트가 요청한 지도 범위를 검증하고 해당 영역의 콘텐츠를 조회합니다. */
@Service
public class MapContentService {

    private static final int CLUSTER_MAX_ZOOM = 14;
    private static final int REPRESENTATIVE_LIMIT = 20;
    private static final double DETAIL_MAX_DIAGONAL_KILOMETERS = 20.0;
    private static final double EARTH_RADIUS_KILOMETERS = 6_371.0088;

    private final MapContentQuery mapContentQuery;
    private final MapContentPersonalizationQuery personalizationQuery;
    private final MapContentCommonCache commonCache;
    private final MapPerformanceMetrics performanceMetrics;

    public MapContentService(
            MapContentQuery mapContentQuery,
            MapContentPersonalizationQuery personalizationQuery,
            MapContentCommonCache commonCache,
            MapPerformanceMetrics performanceMetrics) {
        this.mapContentQuery = mapContentQuery;
        this.personalizationQuery = personalizationQuery;
        this.commonCache = commonCache;
        this.performanceMetrics = performanceMetrics;
    }

    @Transactional(readOnly = true)
    public MapContentResponse getContents(
            Long memberId,
            double south,
            double west,
            double north,
            double east,
            int zoom) {
        validateBounds(south, west, north, east);

        if (zoom <= CLUSTER_MAX_ZOOM
                || diagonalKilometers(south, west, north, east)
                > DETAIL_MAX_DIAGONAL_KILOMETERS) {
            List<MapClusterQueryResult> results = performanceMetrics.recordCommonQuery(
                    () -> mapContentQuery.findClustersWithinBounds(
                            south, west, north, east,
                            gridSize(zoom, south, west, north, east)));
            List<MapContentCommonData> representatives = results.stream()
                    .limit(REPRESENTATIVE_LIMIT)
                    .map(MapClusterQueryResult::representative)
                    .toList();
            return MapContentResponse.cluster(
                    results.stream().map(MapClusterQueryResult::cluster).toList(),
                    personalize(memberId, representatives));
        }

        List<MapContentCommonData> contents = commonCache.get(
                south, west, north, east);
        return MapContentResponse.content(personalize(memberId, contents));
    }

    private List<MapContentItemResponse> personalize(
            Long memberId,
            List<MapContentCommonData> contents) {
        List<Long> contentIds = contents.stream()
                .map(MapContentCommonData::id)
                .distinct()
                .toList();
        MapContentPersonalization personalization = performanceMetrics.recordPersonalizationQuery(
                () -> personalizationQuery.findByMemberAndContentIds(memberId, contentIds));
        return performanceMetrics.recordResponseMapping(
                () -> contents.stream()
                        .map(content -> content.toResponse(
                                personalization.isFavorite(content.id()),
                                personalization.isInGuidebook(content.id())))
                        .toList());
    }

    private double gridSize(
            int zoom,
            double south,
            double west,
            double north,
            double east) {
        double maximumSpan = Math.max(north - south, east - west);
        if (maximumSpan > 4.0) {
            return 1.0;
        }
        if (maximumSpan > 2.0) {
            return 0.5;
        }
        if (maximumSpan > 0.8) {
            return 0.2;
        }
        if (zoom <= 8) {
            return 1.0;
        }
        if (zoom <= 10) {
            return 0.5;
        }
        if (zoom <= 12) {
            return 0.2;
        }
        return 0.05;
    }

    private double diagonalKilometers(
            double south,
            double west,
            double north,
            double east) {
        double latitudeDistance = Math.toRadians(north - south);
        double longitudeDistance = Math.toRadians(east - west);
        double southRadians = Math.toRadians(south);
        double northRadians = Math.toRadians(north);
        double haversine = Math.pow(Math.sin(latitudeDistance / 2.0), 2.0)
                + Math.cos(southRadians)
                * Math.cos(northRadians)
                * Math.pow(Math.sin(longitudeDistance / 2.0), 2.0);
        return 2.0 * EARTH_RADIUS_KILOMETERS
                * Math.asin(Math.min(1.0, Math.sqrt(haversine)));
    }

    private void validateBounds(double south, double west, double north, double east) {
        if (!validLatitude(south) || !validLatitude(north)) {
            throw validationError("latitude", "out_of_range");
        }
        if (!validLongitude(west) || !validLongitude(east)) {
            throw validationError("longitude", "out_of_range");
        }
        if (south >= north) {
            throw validationError("south,north", "invalid_bounds");
        }
        if (west >= east) {
            throw validationError("west,east", "invalid_bounds");
        }
    }

    private boolean validLatitude(double latitude) {
        return Double.isFinite(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    private boolean validLongitude(double longitude) {
        return Double.isFinite(longitude) && longitude >= -180.0 && longitude <= 180.0;
    }

    private BusinessException validationError(String field, String reason) {
        return new BusinessException(
                CommonErrorCode.COMMON_VALIDATION_ERROR,
                List.of(new ErrorDetail(field, reason)));
    }
}
