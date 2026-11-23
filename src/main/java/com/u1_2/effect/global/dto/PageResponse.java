package com.u1_2.effect.global.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 응답의 {@code content}.
 * {@code page}는 1부터 시작한다.
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return of(page.map(mapper));
    }
}
