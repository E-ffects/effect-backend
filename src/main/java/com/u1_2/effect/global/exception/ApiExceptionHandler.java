package com.u1_2.effect.global.exception;

import com.u1_2.effect.global.dto.ResponseDto;
import com.u1_2.effect.rma.domain.RmaErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Spring MVC 표준 예외는 ResponseEntityExceptionHandler가 상태를 정하고, 본문만 ResponseDto로 바꿈.
 * 4xx는 warn, 5xx만 error로 남김.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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

    /** 파라미터 제약 위반(@Validated 클래스의 메서드 검증) */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseDto<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Bad request: ConstraintViolationException - {}", e.getMessage());
        return toResponse(CommonErrorCode.INVALID_INPUT);
    }

    /** multipart 본문을 읽지 못함(형식 오류). 크기 초과는 상위 처리기가 413으로 넘겨 handleExceptionInternal에서 처리 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ResponseDto<Void>> handleMultipart(MultipartException e) {
        log.warn("Multipart 요청을 읽을 수 없음: {}", e.getMessage());
        return toResponse(RmaErrorCode.UNSUPPORTED_FILE);
    }

    /** 메서드 보안(@PreAuthorize 등)에서 거부. 필터 단계 거부는 RestAccessDeniedHandler가 처리 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseDto<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return toResponse(AuthErrorCode.ACCESS_DENIED);
    }

    /** 중복 키·FK 위반 등 동시 요청이나 오래된 화면으로 생기는 충돌 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseDto<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        return toResponse(CommonErrorCode.CONFLICT);
    }

    /** 예상하지 못한 예외: 메시지는 노출하지 않고 로그에만 남긴다. 클라이언트가 끊은 연결은 응답할 수 없으니 debug로만 남김 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDto<Void>> handleUnexpected(Exception e) {
        if (isClientDisconnect(e)) {
            log.debug("Client disconnected: {}", NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            return null;
        }
        log.error("Unhandled exception", e);
        return toResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    /** 응답을 쓰는 중 클라이언트가 연결을 끊음 */
    @Override
    protected ResponseEntity<Object> handleAsyncRequestNotUsableException(
            AsyncRequestNotUsableException ex, WebRequest request) {
        log.debug("Client disconnected: {}", ex.getMessage());
        return null;
    }

    /** @Valid 검증 실패: 응답은 한 줄, 필드별 상세는 로그에만 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Validation failed: {}", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + "=" + fe.getDefaultMessage())
                .toList());
        return body(CommonErrorCode.INVALID_INPUT, headers);
    }

    /** 그 밖의 Spring MVC 표준 예외: 상태에 맞는 ErrorCode로 본문을 맞춤 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Spring MVC exception: {}", ex.getClass().getSimpleName(), ex);
        } else if (ex instanceof NoResourceFoundException notFound) {
            log.warn("No resource: {}", notFound.getResourcePath());
        } else {
            log.warn("Bad request: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
        return body(errorCodeFor(status), headers);
    }

    private ErrorCode errorCodeFor(HttpStatusCode status) {
        // 기존 응답과 맞춤: 404·405는 그대로, 413(업로드 크기)은 첨부 오류, 그 밖의 4xx는 400 INVALID_INPUT
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return CommonErrorCode.NOT_FOUND;
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return CommonErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status.value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            return RmaErrorCode.UNSUPPORTED_FILE;
        }
        if (status.is4xxClientError()) {
            return CommonErrorCode.INVALID_INPUT;
        }
        return CommonErrorCode.INTERNAL_ERROR;
    }

    private static boolean isClientDisconnect(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AsyncRequestNotUsableException
                    || t.getClass().getSimpleName().equals("ClientAbortException")) {
                return true;
            }
            if (t instanceof IOException && t.getMessage() != null) {
                String message = t.getMessage().toLowerCase();
                if (message.contains("broken pipe") || message.contains("connection reset")) {
                    return true;
                }
            }
        }
        return false;
    }

    private ResponseEntity<Object> body(ErrorCode errorCode, HttpHeaders headers) {
        // 상위 처리기가 넣은 헤더(405의 Allow 등)는 유지함
        return ResponseEntity.status(errorCode.getHttpStatus()).headers(headers).body(ResponseDto.ofFailure(errorCode));
    }

    private ResponseEntity<ResponseDto<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ResponseDto.ofFailure(errorCode));
    }
}
