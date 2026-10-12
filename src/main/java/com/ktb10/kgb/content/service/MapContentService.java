package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.error.ErrorDetail;
import com.ktb10.kgb.common.observability.MapPerformanceMetrics;
import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentListResponse;
import com.ktb10.kgb.content.dto.MapMarkerItemResponse;
import com.ktb10.kgb.content.dto.MapMarkerResponse;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import com.ktb10.kgb.content.repository.MapContentPersonalization;
import com.ktb10.kgb.content.repository.MapContentPersonalizationQuery;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 클라이언트가 요청한 지도 범위를 검증하고 해당 영역의 콘텐츠를 조회합니다. */
@Service
public class MapContentService {

    private static final int MIN_ZOOM = 6;
    private static final int MAX_ZOOM = 21;
    private static final int CLUSTER_MAX_ZOOM = 14;
    private static final int BOTTOM_SHEET_PAGE_SIZE = 10;
    private static final double DETAIL_MAX_DIAGONAL_KILOMETERS = 20.0;
    private static final double EARTH_RADIUS_KILOMETERS = 6_371.0088;

    private final MapContentRadiusClusterer radiusClusterer;
    private final MapContentPersonalizationQuery personalizationQuery;
    private final MapContentCommonCache commonCache;
    private final MapPerformanceMetrics performanceMetrics;

    public MapContentService(
            MapContentRadiusClusterer radiusClusterer,
            MapContentPersonalizationQuery personalizationQuery,
            MapContentCommonCache commonCache,
            MapPerformanceMetrics performanceMetrics) {
        this.radiusClusterer = radiusClusterer;
        this.personalizationQuery = personalizationQuery;
        this.commonCache = commonCache;
        this.performanceMetrics = performanceMetrics;
    }

    @Transactional(readOnly = true)
    public MapMarkerResponse getMarkers(
            Long memberId,
            double south,
            double west,
            double north,
            double east,
            int zoom) {
        validateBounds(south, west, north, east);
        validateZoom(zoom);

        boolean wideBounds = diagonalKilometers(south, west, north, east)
                > DETAIL_MAX_DIAGONAL_KILOMETERS;
        if (zoom <= CLUSTER_MAX_ZOOM || wideBounds) {
            int clusterZoom = wideBounds ? Math.min(zoom, CLUSTER_MAX_ZOOM) : zoom;
            List<MapContentRadiusClusterer.Result> results = radiusClusterer.cluster(
                    commonCache.get(south, west, north, east), clusterZoom);
            return MapMarkerResponse.cluster(
                    results.stream().map(MapContentRadiusClusterer.Result::cluster).toList());
        }

        List<MapContentCommonData> contents = commonCache.get(
                south, west, north, east);
        return MapMarkerResponse.marker(personalizeMarkers(memberId, contents));
    }

    @Transactional(readOnly = true)
    public MapContentListResponse getBottomSheetContents(
            Long memberId,
            double south,
            double west,
            double north,
            double east,
            String cursor) {
        validateBounds(south, west, north, east);

        List<MapContentCommonData> sortedContents = commonCache.get(south, west, north, east)
                .stream()
                .sorted(Comparator
                        .comparingDouble((MapContentCommonData content) -> distanceSquared(
                                content,
                                (south + north) / 2.0,
                                (west + east) / 2.0))
                        .thenComparing(MapContentCommonData::id))
                .toList();
        int boundsHash = Objects.hash(south, west, north, east);
        int offset = decodeCursor(cursor, boundsHash);
        if (offset > sortedContents.size()) {
            throw validationError("cursor", "out_of_range");
        }

        int endExclusive = Math.min(offset + BOTTOM_SHEET_PAGE_SIZE, sortedContents.size());
        List<MapContentCommonData> page = sortedContents.subList(offset, endExclusive);
        boolean hasMore = endExclusive < sortedContents.size();
        String nextCursor = hasMore ? encodeCursor(endExclusive, boundsHash) : null;
        return new MapContentListResponse(
                personalize(memberId, page),
                nextCursor,
                hasMore);
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

    private List<MapMarkerItemResponse> personalizeMarkers(
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
                        .map(content -> content.toMarkerResponse(
                                personalization.isFavorite(content.id()),
                                personalization.isInGuidebook(content.id())))
                        .toList());
    }

    private double distanceSquared(
            MapContentCommonData content,
            double centerLatitude,
            double centerLongitude) {
        double latitude = content.latitude() - centerLatitude;
        double longitude = content.longitude() - centerLongitude;
        return latitude * latitude + longitude * longitude;
    }

    private String encodeCursor(int offset, int boundsHash) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 2);
        buffer.putInt(offset);
        buffer.putInt(boundsHash);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private int decodeCursor(String cursor, int boundsHash) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            if (decoded.length != Integer.BYTES * 2) {
                throw validationError("cursor", "invalid_format");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            int offset = buffer.getInt();
            int cursorBoundsHash = buffer.getInt();
            if (offset < 0 || cursorBoundsHash != boundsHash) {
                throw validationError("cursor", "invalid_for_bounds");
            }
            return offset;
        } catch (IllegalArgumentException exception) {
            throw validationError("cursor", "invalid_format");
        }
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

    private void validateZoom(int zoom) {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw validationError("zoom", "out_of_range");
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
