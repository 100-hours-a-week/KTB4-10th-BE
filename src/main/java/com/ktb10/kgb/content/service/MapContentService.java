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

/** 클라이언트가 요청한 지도 범위를 검증하고 해당 영역의 콘텐츠를 조회합니다. */
@Service
public class MapContentService {

    private final MapContentQuery mapContentQuery;

    public MapContentService(MapContentQuery mapContentQuery) {
        this.mapContentQuery = mapContentQuery;
    }

    @Transactional(readOnly = true)
    public MapContentResponse getContents(
            Long memberId,
            double south,
            double west,
            double north,
            double east) {
        validateBounds(south, west, north, east);

        List<MapContentItemResponse> items = mapContentQuery.findWithinBounds(
                memberId, south, west, north, east);
        return new MapContentResponse(items, false);
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
