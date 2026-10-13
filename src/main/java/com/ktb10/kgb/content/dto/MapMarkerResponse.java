package com.ktb10.kgb.content.dto;

import java.util.List;

/** 지도에 표시할 클러스터 또는 개별 마커 응답입니다. */
public record MapMarkerResponse(
        Mode mode,
        List<MapClusterResponse> clusters,
        List<MapMarkerItemResponse> markers) {

    public MapMarkerResponse {
        clusters = List.copyOf(clusters);
        markers = List.copyOf(markers);
    }

    public static MapMarkerResponse marker(List<MapMarkerItemResponse> markers) {
        return new MapMarkerResponse(Mode.MARKER, List.of(), markers);
    }

    public static MapMarkerResponse cluster(List<MapClusterResponse> clusters) {
        return new MapMarkerResponse(Mode.CLUSTER, clusters, List.of());
    }

    public enum Mode {
        MARKER,
        CLUSTER
    }
}
