package com.ktb10.kgb.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** MVC 계층의 예외를 공통 오류 응답으로 변환합니다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        return respond(exception.errorCode(), exception.details(), request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException exception,
            HttpServletRequest request) {
        List<ErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new ErrorDetail(
                        error.getField(),
                        validationReason(error.getCode())))
                .distinct()
                .toList();

        return respond(CommonErrorCode.COMMON_VALIDATION_ERROR, details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<ErrorDetail> details = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorDetail(
                        violation.getPropertyPath().toString(),
                        validationReason(violation.getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName())))
                .sorted(Comparator.comparing(ErrorDetail::field))
                .distinct()
                .toList();

        return respond(CommonErrorCode.COMMON_VALIDATION_ERROR, details, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        ErrorDetail detail = new ErrorDetail(exception.getName(), "invalid_type");
        return respond(CommonErrorCode.COMMON_VALIDATION_ERROR, List.of(detail), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameterException(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        ErrorDetail detail = new ErrorDetail(exception.getParameterName(), "required");
        return respond(CommonErrorCode.COMMON_VALIDATION_ERROR, List.of(detail), request);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<ErrorResponse> handleUnreadableRequestException(
            Exception exception,
            HttpServletRequest request) {
        return respond(CommonErrorCode.COMMON_VALIDATION_ERROR, List.of(), request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            Exception exception,
            HttpServletRequest request) {
        return respond(CommonErrorCode.RESOURCE_NOT_FOUND, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        String traceId = TraceId.getOrCreate(request);
        LOGGER.error("Unhandled exception [traceId={}]", traceId, exception);
        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.httpStatus())
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR, traceId));
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode errorCode,
            List<ErrorDetail> details,
            HttpServletRequest request) {
        String traceId = TraceId.getOrCreate(request);
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ErrorResponse.of(errorCode, details, traceId));
    }

    private String validationReason(String validationCode) {
        if (validationCode == null) {
            return "invalid";
        }

        return switch (validationCode) {
            case "NotBlank", "NotEmpty", "NotNull" -> "required";
            case "Size" -> "invalid_size";
            case "Min", "Max", "DecimalMin", "DecimalMax" -> "out_of_range";
            case "Pattern" -> "invalid_format";
            case "Email" -> "invalid_email";
            default -> "invalid";
        };
    }
}
