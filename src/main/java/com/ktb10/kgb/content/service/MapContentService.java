package com.ktb10.kgb.content.service;

import com.ktb10.kgb.common.error.BusinessException;
import com.ktb10.kgb.common.error.CommonErrorCode;
import com.ktb10.kgb.common.error.ErrorDetail;
import com.ktb10.kgb.content.dto.MapContentItemResponse;
import com.ktb10.kgb.content.dto.MapContentResponse;
import com.ktb10.kgb.content.repository.MapContentQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지도 범위 검증과 콘텐츠 조회 상한을 관리합니다. */
@Service
public class MapContentService {

    static final double MAX_DIAGONAL_KILOMETERS = 20.0;
    private static final double EARTH_RADIUS_KILOMETERS = 6_371.0088;

    private final MapContentQuery mapContentQuery;

    public MapContentService(MapContentQuery mapContentQuery) {
        this.mapContentQuery = mapContentQuery;
    }

    @Transactional(readOnly = true)
    public MapContentResponse getContents(
            double south,
            double west,
            double north,
            double east,
            int limit) {
        validateBounds(south, west, north, east);

        List<MapContentItemResponse> candidates = mapContentQuery.findWithinBounds(
                south, west, north, east, limit + 1);
        boolean hasMore = candidates.size() > limit;
        List<MapContentItemResponse> items = hasMore
                ? candidates.subList(0, limit)
                : candidates;
        return new MapContentResponse(items, hasMore);
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
        if (diagonalKilometers(south, west, north, east)
                > MAX_DIAGONAL_KILOMETERS) {
            throw validationError("bounds", "area_too_large");
        }
    }

    private boolean validLatitude(double latitude) {
        return Double.isFinite(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    private boolean validLongitude(double longitude) {
        return Double.isFinite(longitude) && longitude >= -180.0 && longitude <= 180.0;
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

    private BusinessException validationError(String field, String reason) {
        return new BusinessException(
                CommonErrorCode.COMMON_VALIDATION_ERROR,
                List.of(new ErrorDetail(field, reason)));
    }
}

