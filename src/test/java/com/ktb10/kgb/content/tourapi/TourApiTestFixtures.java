package com.ktb10.kgb.content.tourapi;

import java.net.URI;
import java.time.Duration;

final class TourApiTestFixtures {

    private TourApiTestFixtures() {
    }

    static TourApiProperties properties(String snapshotLocation) {
        return new TourApiProperties(
                URI.create("https://apis.data.go.kr/B551011/KorService2"),
                "service-key",
                snapshotLocation,
                "ETC",
                "KGB",
                60000,
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                64 * 1024 * 1024,
                90,
                2);
    }
}
