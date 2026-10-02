package com.ktb10.kgb.tools.tourapi;

import com.ktb10.kgb.KgbApplication;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** TourAPI 전국 목록을 수집·저장·증분 적재하고 종료하는 실행기입니다. */
public final class TourApiSnapshotApplication {

    private TourApiSnapshotApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(KgbApplication.class);
        application.setDefaultProperties(Map.of(
                "server.port", "0",
                "spring.profiles.default", "local"));

        try (ConfigurableApplicationContext context = application.run(args)) {
            TourApiAreaCollectionService collector =
                    context.getBean(TourApiAreaCollectionService.class);
            System.out.println(collector.collectAndImport().toDisplayText());
        }
    }
}
