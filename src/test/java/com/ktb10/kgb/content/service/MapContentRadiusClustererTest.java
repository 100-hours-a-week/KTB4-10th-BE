package com.ktb10.kgb.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb10.kgb.content.dto.MapContentItemResponse.ContentType;
import com.ktb10.kgb.content.repository.MapContentCommonData;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapContentRadiusClustererTest {

    private final MapContentRadiusClusterer clusterer = new MapContentRadiusClusterer();

    @Test
    void groupsContentsWithinKakaoMapPixelRadius() {
        List<MapContentRadiusClusterer.Result> results = clusterer.cluster(
                List.of(content(1L, 37.5, 126.9), content(2L, 37.5, 126.91)),
                14);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().cluster().count()).isEqualTo(2);
    }

    @Test
    void keepsDistantContentsInSeparateClusters() {
        List<MapContentRadiusClusterer.Result> results = clusterer.cluster(
                List.of(content(1L, 37.5, 126.9), content(2L, 37.6, 127.0)),
                14);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(result -> result.cluster().count())
                .containsExactly(1, 1);
    }

    @Test
    void producesStableResultRegardlessOfInputOrder() {
        MapContentCommonData first = content(1L, 37.5, 126.9);
        MapContentCommonData second = content(2L, 37.5001, 126.9001);

        List<MapContentRadiusClusterer.Result> forward = clusterer.cluster(
                List.of(first, second), 14);
        List<MapContentRadiusClusterer.Result> reversed = clusterer.cluster(
                List.of(second, first), 14);

        assertThat(reversed).isEqualTo(forward);
    }

    @Test
    void groupsContentsWithinOneHundredTwentyPixelRadius() {
        List<MapContentRadiusClusterer.Result> results = clusterer.cluster(
                List.of(content(1L, 37.5, 126.9), content(2L, 37.5, 126.93)),
                14);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().cluster().count()).isEqualTo(2);
    }

    private MapContentCommonData content(Long id, double latitude, double longitude) {
        return new MapContentCommonData(
                id,
                String.valueOf(id),
                "장소 " + id,
                ContentType.PLACE,
                "주소",
                latitude,
                longitude,
                null,
                null);
    }
}
