package com.ktb10.kgb.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb10.kgb.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void successResponseContainsOnlyMessageAndData() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceId.HEADER_NAME))
                .andExpect(jsonPath("$.message").value("test_success"))
                .andExpect(jsonPath("$.data.value").value("result"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void beanValidationErrorContainsStableFieldDetailAndTraceId() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"))
                .andExpect(jsonPath("$.error.details[0].reason").value("required"))
                .andReturn();

        String traceId = result.getResponse().getHeader(TraceId.HEADER_NAME);
        assertThat(traceId).isNotBlank();
        assertThat(result.getResponse().getContentAsString())
                .contains("\"trace_id\":\"" + traceId + "\"");
    }

    @Test
    void businessExceptionKeepsItsStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.error.trace_id").isNotEmpty());
    }

    @Test
    void malformedJsonBecomesValidationError() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isEmpty());
    }

    @Test
    void parameterTypeMismatchContainsParameterDetail() throws Exception {
        mockMvc.perform(get("/test/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("value"))
                .andExpect(jsonPath("$.error.details[0].reason").value("invalid_type"));
    }

    @Test
    void queryAndPathValidationErrorsContainSortedFieldDetails() throws Exception {
        mockMvc.perform(get("/test/search/x").param("page", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(2))
                .andExpect(jsonPath("$.error.details[0].field").value("code"))
                .andExpect(jsonPath("$.error.details[0].reason").value("invalid_size"))
                .andExpect(jsonPath("$.error.details[1].field").value("page"))
                .andExpect(jsonPath("$.error.details[1].reason").value("out_of_range"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeOriginalMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(not(containsString("sensitive database detail"))));
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        @GetMapping("/success")
        ApiResponse<Map<String, String>> success() {
            return ApiResponse.success("test_success", Map.of("value", "result"));
        }

        @PostMapping("/validation")
        ApiResponse<TestRequest> validate(@Valid @RequestBody TestRequest request) {
            return ApiResponse.success("test_success", request);
        }

        @GetMapping("/business-error")
        void businessError() {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("sensitive database detail");
        }

        @GetMapping("/number")
        int number(@RequestParam Integer value) {
            return value;
        }

        @GetMapping("/search/{code}")
        int search(
                @PathVariable(name = "code") @Size(min = 2, max = 5) String code,
                @RequestParam(name = "page") @Min(1) @Max(10) int page) {
            return code.length() + page;
        }
    }

    private record TestRequest(@NotBlank String name) {
    }
}
