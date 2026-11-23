package com.u1_2.effect.global.dto;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.CommonErrorCode;

public record PageParameters(int page, int size) {

    public PageParameters {
        if (page < 1 || size < 1 || size > 100) {
            throw new ApiException(CommonErrorCode.INVALID_INPUT);
        }
    }

    public static PageParameters resolve(Integer page, Integer size) {
        return resolve(page, size, 30);
    }

    public static PageParameters resolve(Integer page, Integer size, int defaultSize) {
        return new PageParameters(page == null ? 1 : page, size == null ? defaultSize : size);
    }

    // API 페이지는 1부터, DB 오프셋은 0부터 시작함
    public long offset() {
        return (page - 1L) * size;
    }
}
