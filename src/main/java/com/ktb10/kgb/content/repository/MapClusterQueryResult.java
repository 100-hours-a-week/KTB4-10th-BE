package com.ktb10.kgb.content.repository;

import com.ktb10.kgb.content.dto.MapClusterResponse;
import com.ktb10.kgb.content.dto.MapContentItemResponse;

/** 클러스터 집계와 해당 격자의 대표 콘텐츠입니다. */
public record MapClusterQueryResult(
        MapClusterResponse cluster,
        MapContentItemResponse representative) {
}
