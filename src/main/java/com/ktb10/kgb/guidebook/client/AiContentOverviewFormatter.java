package com.ktb10.kgb.guidebook.client;

import java.util.regex.Pattern;

/** TourAPI overview를 AI 요청 계약에 맞게 공백 정규화합니다. */
final class AiContentOverviewFormatter {

    private static final Pattern CONTINUOUS_WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private AiContentOverviewFormatter() {
    }

    static String format(String overview) {
        if (overview == null) {
            return "";
        }
        return CONTINUOUS_WHITESPACE.matcher(overview)
                .replaceAll(" ")
                .strip();
    }
}
