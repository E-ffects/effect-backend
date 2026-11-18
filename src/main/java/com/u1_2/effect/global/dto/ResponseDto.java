package com.u1_2.effect.global.dto;

import com.u1_2.effect.global.exception.ErrorCode;

/**
 * 모든 API 응답의 공통 형식.
 * <pre>
 * { "code": "SUCCESS", "message": "요청이 성공적으로 처리되었습니다.", "content": { ... } }
 * { "code": "RMA_NOT_FOUND", "message": "RMA를 찾을 수 없습니다.", "content": null }
 * </pre>
 * 생성은 정적 팩토리 메서드로만 한다.
 */
public record ResponseDto<D>(
        String code,
        String message,
        D content
) {
    private static final String SUCCESS_CODE = "SUCCESS";

    public static <D> ResponseDto<D> ofSuccess(SuccessMessage success, D content) {
        return new ResponseDto<>(SUCCESS_CODE, success.getMessage(), content);
    }

    public static <D> ResponseDto<D> ofSuccess(D content) {
        return ofSuccess(SuccessMessage.OPERATION_SUCCESS, content);
    }

    public static ResponseDto<Void> ofSuccess(SuccessMessage success) {
        return new ResponseDto<>(SUCCESS_CODE, success.getMessage(), null);
    }

    public static ResponseDto<Void> ofFailure(ErrorCode errorCode) {
        return new ResponseDto<>(errorCode.getErrorCode(), errorCode.getMessage(), null);
    }
}
