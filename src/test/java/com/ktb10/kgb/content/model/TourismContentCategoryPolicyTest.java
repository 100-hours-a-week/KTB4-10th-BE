package com.ktb10.kgb.content.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TourismContentCategoryPolicyTest {

    @Test
    void mapsNaturalAndUrbanParksToTheSameServiceCategory() {
        TourismContentCategoryPolicy naturalPark =
                TourismContentCategoryPolicy.fromSourceCode("NA04").orElseThrow();
        TourismContentCategoryPolicy urbanPark =
                TourismContentCategoryPolicy.fromSourceCode("VE03").orElseThrow();

        assertThat(naturalPark.theme()).isEqualTo(urbanPark.theme());
        assertThat(naturalPark.detail()).isEqualTo(urbanPark.detail());
        assertThat(naturalPark.detail().code()).isEqualTo("NATURE_PARK");
        assertThat(naturalPark.detail().label()).isEqualTo("공원");
    }

    @Test
    void exposesFrontendLabelsAndOrdering() {
        TourismContentCategoryPolicy natureEtc =
                TourismContentCategoryPolicy.fromSourceCode("NA05").orElseThrow();
        TourismContentCategoryPolicy experienceEtc =
                TourismContentCategoryPolicy.fromSourceCode("EX07").orElseThrow();

        assertThat(natureEtc.detail().label()).isEqualTo("그 외");
        assertThat(natureEtc.detail().sortOrder()).isEqualTo(50);
        assertThat(experienceEtc.detail().label()).isEqualTo("그 외 체험");
        assertThat(experienceEtc.detail().sortOrder()).isEqualTo(70);
    }

    @Test
    void excludesUnsupportedCultureTourismClassifications() {
        List<String> excludedCodes =
                List.of("VE05", "VE06", "VE08", "VE09", "VE10", "VE11", "VE12");

        assertThat(TourismContentCategoryPolicy.allowedSourceCodes())
                .doesNotContainAnyElementsOf(excludedCodes);
        excludedCodes.forEach(code -> assertThat(
                TourismContentCategoryPolicy.fromSourceCode(code)).isEmpty());
    }
}
