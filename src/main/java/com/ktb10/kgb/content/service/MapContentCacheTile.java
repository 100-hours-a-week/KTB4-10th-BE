package com.ktb10.kgb.content.service;

/** 0.5도 지도 캐시 타일의 영속 식별자입니다. */
public record MapContentCacheTile(long latitudeCell, long longitudeCell) {

    private static final double TILE_SIZE = 0.5;

    public static MapContentCacheTile from(MapContentCacheLocation location) {
        return new MapContentCacheTile(
                cellOf(location.latitude()),
                cellOf(location.longitude()));
    }

    double south() {
        return latitudeCell * TILE_SIZE;
    }

    double west() {
        return longitudeCell * TILE_SIZE;
    }

    double north() {
        return south() + TILE_SIZE;
    }

    double east() {
        return west() + TILE_SIZE;
    }

    static long cellOf(double coordinate) {
        return (long) Math.floor(coordinate / TILE_SIZE);
    }
}
