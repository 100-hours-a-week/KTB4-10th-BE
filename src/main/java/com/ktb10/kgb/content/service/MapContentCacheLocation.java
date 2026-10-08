package com.ktb10.kgb.content.service;

/** 관광 콘텐츠 변경 전·후 위치를 캐시 갱신 대상으로 전달하기 위한 좌표입니다. */
public record MapContentCacheLocation(double latitude, double longitude) {

    public MapContentCacheLocation {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("위도가 유효한 범위를 벗어났습니다.");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("경도가 유효한 범위를 벗어났습니다.");
        }
    }
}
