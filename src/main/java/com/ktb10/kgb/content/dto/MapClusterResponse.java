package com.ktb10.kgb.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 광역 지도에 표시할 격자 클러스터입니다. */
public record MapClusterResponse(
        @JsonProperty("cluster_id")
        String clusterId,
        double latitude,
        double longitude,
        int count) {
}
