package com.ktb10.kgb.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb10.kgb.common.error.ErrorCode;
import com.ktb10.kgb.common.error.ErrorResponse;
import com.ktb10.kgb.common.error.TraceId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** MVC 진입 전 보안 실패를 공통 JSON 오류 형식으로 기록합니다. */
@Component
public class RestSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public RestSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        String traceId = TraceId.getOrCreate(request);
        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(TraceId.HEADER_NAME, traceId);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode, traceId));
    }
}
