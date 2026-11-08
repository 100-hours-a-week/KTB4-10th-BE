package com.ktb10.kgb.guidebook.dto;

/** AI 입력 거절 코드는 명시적으로 분류하고 내부 응답 원문은 사용자에게 노출하지 않습니다. */
public final class GenerationFailurePolicy {

    private GenerationFailurePolicy() {
    }

    public static boolean requiresCorrection(String code) {
        return inputMessage(code) != null;
    }

    public static String inputMessage(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "invalid_request" -> "AI가 생성 입력을 처리하지 못했어요. 여행 조건과 요청 내용을 확인해 주세요.";
            case "invalid_date_range" -> "여행 날짜를 처리할 수 없어요. 여행 기간을 확인해 주세요.";
            case "invalid_preference_mapping" -> "저장된 여행 취향을 처리할 수 없어요. 취향을 다시 선택해 주세요.";
            case "feedback_not_applicable" -> "입력한 피드백을 일정에 반영할 수 없어요. 요청 내용을 구체적으로 수정해 주세요.";
            case "insufficient_candidates" -> "여행 일정에 필요한 장소가 부족해요. 지역이나 여행 기간을 변경해 주세요.";
            default -> null;
        };
    }
}
