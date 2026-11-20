package com.u1_2.effect.global.exception;

import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ResponseDto<Void>> handleApiException(ApiException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("ApiException: {}", errorCode.getErrorCode(), e);
        } else {
            log.warn("ApiException: {}", errorCode.getErrorCode());
        }
        return toResponse(errorCode);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseDto<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Multipart 파일 또는 요청 크기 초과");
        return toResponse(RmaErrorCode.UNSUPPORTED_FILE);
    }

    /** @Valid 검증 실패: 응답은 한 줄, 필드별 상세는 로그에만 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDto<Void>> handleValidation(MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + "=" + fe.getDefaultMessage())
                .toList());
        return toResponse(CommonErrorCode.INVALID_INPUT);
    }

    /** 요청 형식 오류: 파라미터·경로 변수 검증 실패, JSON 파싱 실패, 타입 불일치, 필수 파라미터 누락 */
    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ResponseDto<Void>> handleBadRequest(Exception e) {
        log.warn("Bad request: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        return toResponse(CommonErrorCode.INVALID_INPUT);
    }

    /** 없는 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseDto<Void>> handleNotFound(NoResourceFoundException e) {
        log.warn("No resource: {}", e.getResourcePath());
        return toResponse(CommonErrorCode.NOT_FOUND);
    }

    /** 허용되지 않은 HTTP 메서드 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseDto<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMethod());
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 예상하지 못한 예외: 메시지는 노출하지 않고 로그에만 남긴다 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDto<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ResponseDto<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ResponseDto.ofFailure(errorCode));
    }
}
