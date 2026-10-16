package com.ktb10.kgb.guidebook.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiContentOverviewFormatterTest {

    @Test
    void normalizesUnicodeWhitespaceAndStripsOnlyBeforeTruncation() {
        String overview = "  첨성대\n\t는\u00A0신라  시대의 유적입니다.  ";

        assertThat(AiContentOverviewFormatter.format(overview))
                .isEqualTo("첨성대 는 신라 시대의 유적입니다.");
    }

    @Test
    void keepsTextThroughLastPeriodWhenPeriodIndexIsAtLeastOneHundred() {
        String overview = "가".repeat(100) + "." + "나".repeat(150);

        assertThat(AiContentOverviewFormatter.format(overview))
                .isEqualTo("가".repeat(100) + ".");
    }

    @Test
    void keepsTwoHundredCharactersWhenLastPeriodIsBeforeIndexOneHundred() {
        String overview = "가".repeat(99) + "." + "나".repeat(150);

        assertThat(AiContentOverviewFormatter.format(overview))
                .hasSize(200)
                .isEqualTo(overview.substring(0, 200));
    }

    @Test
    void returnsEmptyStringWhenOverviewIsMissing() {
        assertThat(AiContentOverviewFormatter.format(null)).isEmpty();
        assertThat(AiContentOverviewFormatter.format("   \n\t  ")).isEmpty();
    }
}
