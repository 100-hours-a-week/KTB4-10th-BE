package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 지도 범위에 포함된 콘텐츠 목록과 추가 결과 여부입니다. */
public record MapContentResponse(
        Mode mode,
        List<MapClusterResponse> clusters,
        List<MapContentItemResponse> items,
        @JsonProperty("has_more")
        boolean hasMore) {

    public MapContentResponse {
        clusters = List.copyOf(clusters);
        items = List.copyOf(items);
    }

    public static MapContentResponse content(List<MapContentItemResponse> items) {
        return new MapContentResponse(Mode.CONTENT, List.of(), items, false);
    }

    public static MapContentResponse cluster(
            List<MapClusterResponse> clusters,
            List<MapContentItemResponse> representatives) {
        return new MapContentResponse(Mode.CLUSTER, clusters, representatives, false);
    }

    public enum Mode {
        CONTENT,
        CLUSTER
    }
}
