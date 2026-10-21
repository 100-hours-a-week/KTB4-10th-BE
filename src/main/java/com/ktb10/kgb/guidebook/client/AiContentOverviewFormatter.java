package com.ktb10.kgb.guidebook.client;

import java.util.regex.Pattern;

/** TourAPI overview를 AI 학습 계약에 맞는 고정 길이 요약으로 변환합니다. */
final class AiContentOverviewFormatter {

    private static final int MAX_LENGTH = 200;
    private static final int SENTENCE_END_MIN_INDEX = 100;
    private static final Pattern CONTINUOUS_WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private AiContentOverviewFormatter() {
    }

    static String format(String overview) {
        if (overview == null) {
            return "";
        }
        String normalized = CONTINUOUS_WHITESPACE.matcher(overview)
                .replaceAll(" ")
                .strip();
        String truncated = normalized.substring(0, Math.min(normalized.length(), MAX_LENGTH));
        int sentenceEnd = truncated.lastIndexOf('.');
        return sentenceEnd >= SENTENCE_END_MIN_INDEX
                ? truncated.substring(0, sentenceEnd + 1)
                : truncated;
    }
}
