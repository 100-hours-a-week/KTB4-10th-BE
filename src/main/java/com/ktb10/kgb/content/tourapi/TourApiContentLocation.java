package com.ktb10.kgb.content.tourapi;

/** 변경 전·후 지도 타일을 찾는 데 사용하는 TourAPI 콘텐츠 좌표입니다. */
public record TourApiContentLocation(double latitude, double longitude) {

    public TourApiContentLocation {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("위도가 유효한 범위를 벗어났습니다.");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("경도가 유효한 범위를 벗어났습니다.");
        }
    }
}
