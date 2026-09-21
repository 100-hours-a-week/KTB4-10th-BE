package com.ktb10.kgb.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferenceCodeTest {

    @Test
    void registryKeepsApprovedCodesAndDeclarationOrder() {
        List<String> codes = Arrays.stream(PreferenceCode.values())
                .map(Enum::name)
                .toList();

        assertThat(codes).containsExactly(
                "NATURE",
                "NATURE_MOUNTAIN",
                "NATURE_RIVER_SEA",
                "NATURE_ECOLOGY",
                "NATURE_PARK",
                "NATURE_ETC",
                "HISTORY",
                "HISTORY_HERITAGE_SITE",
                "HISTORY_RELIC",
                "HISTORY_RELIGIOUS_SITE",
                "HISTORY_SECURITY_SITE",
                "ATTRACTION",
                "ATTRACTION_LANDMARK",
                "ATTRACTION_THEME_PARK",
                "ATTRACTION_URBAN_CULTURE",
                "ATTRACTION_EXHIBITION",
                "EXPERIENCE",
                "EXPERIENCE_TRADITION",
                "EXPERIENCE_CRAFT",
                "EXPERIENCE_RURAL",
                "EXPERIENCE_TEMPLE_STAY",
                "EXPERIENCE_HEALING",
                "EXPERIENCE_INDUSTRY",
                "EXPERIENCE_ETC",
                "LEISURE_SPORTS",
                "LEISURE_SPORTS_LAND",
                "LEISURE_SPORTS_WATER",
                "LEISURE_SPORTS_AIR",
                "LEISURE_SPORTS_COMPLEX",
                "EVENTS",
                "EVENTS_FESTIVAL",
                "EVENTS_CONCERT",
                "EVENTS_FAIR",
                "RELAXING",
                "TIME_EFFICIENCY",
                "WALK_FRIENDLY",
                "CAR_TRAVEL");
    }

    @Test
    void registryHasSixThemesTwentySevenDetailsAndFourOptionalStyles() {
        assertThat(count(PreferenceType.THEME)).isEqualTo(6);
        assertThat(count(PreferenceType.DETAIL)).isEqualTo(27);
        assertThat(count(PreferenceType.TRAVEL_STYLE)).isEqualTo(4);
    }

    @Test
    void detailHasThemeParentAndOtherTypesHaveNoParent() {
        assertThat(Arrays.stream(PreferenceCode.values()))
                .allSatisfy(code -> {
                    assertThat(code.label()).isNotBlank();
                    assertThat(code.sortOrder()).isPositive();
                    if (code.type() == PreferenceType.DETAIL) {
                        assertThat(code.parent()).isNotNull();
                        assertThat(code.parent().type()).isEqualTo(PreferenceType.THEME);
                        assertThat(code.parentCode()).isEqualTo(code.parent().name());
                    } else {
                        assertThat(code.parent()).isNull();
                        assertThat(code.parentCode()).isNull();
                    }
                });
    }

    private long count(PreferenceType type) {
        return Arrays.stream(PreferenceCode.values())
                .filter(code -> code.type() == type)
                .count();
    }
}
