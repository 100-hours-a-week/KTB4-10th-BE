package com.ktb10.kgb.tools.tourapi;

import com.ktb10.kgb.KgbApplication;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/** 제공받은 TourAPI JSON을 한 번 적재하고 종료하는 로컬 실행기입니다. */
public final class TourApiImportApplication {

    private TourApiImportApplication() {
    }

    public static void main(String[] args) {
        ImportArguments arguments = ImportArguments.parse(args);
        SpringApplication application = new SpringApplication(KgbApplication.class);
        application.setAdditionalProfiles("local");
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run()) {
            TourApiInitialImportService importer =
                    context.getBean(TourApiInitialImportService.class);
            ImportSummary summary = importer.importFiles(
                    arguments.areaFile(),
                    arguments.festivalFile());
            System.out.println(summary.toDisplayText());
        }
    }

    private record ImportArguments(Path areaFile, Path festivalFile) {

        private static ImportArguments parse(String[] args) {
            Path areaFile = null;
            Path festivalFile = null;
            for (String argument : args) {
                if (argument.startsWith("--area-file=")) {
                    areaFile = Path.of(argument.substring("--area-file=".length()));
                } else if (argument.startsWith("--festival-file=")) {
                    festivalFile = Path.of(argument.substring("--festival-file=".length()));
                }
            }
            if (areaFile == null || festivalFile == null) {
                throw new IllegalArgumentException(
                        "--area-file과 --festival-file을 모두 지정해야 합니다.");
            }
            return new ImportArguments(areaFile, festivalFile);
        }
    }
}
