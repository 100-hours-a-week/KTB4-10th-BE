package com.ktb10.kgb.common.error;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ktb10.kgb.common.response.ApiResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonResponseValidationTest {

    @Test
    void commonResponseObjectsUseKoreanValidationMessages() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ApiResponse<>(null, null))
                .withMessage("응답 메시지는 null일 수 없습니다.");
        assertThatNullPointerException()
                .isThrownBy(() -> new ErrorDetail(null, "required"))
                .withMessage("오류 필드는 null일 수 없습니다.");
        assertThatNullPointerException()
                .isThrownBy(() -> new ErrorPayload("CODE", null, "trace-id"))
                .withMessage("오류 상세 목록은 null일 수 없습니다.");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ErrorResponse(
                        "오류",
                        "data",
                        new ErrorPayload("CODE", List.of(), "trace-id")))
                .withMessage("오류 응답의 데이터는 null이어야 합니다.");
        assertThatNullPointerException()
                .isThrownBy(() -> new BusinessException(null))
                .withMessage("오류 코드는 null일 수 없습니다.");
    }
}
