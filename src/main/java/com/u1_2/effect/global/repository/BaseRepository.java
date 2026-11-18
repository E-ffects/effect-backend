package com.u1_2.effect.global.repository;

import com.u1_2.effect.global.exception.ApiException;
import com.u1_2.effect.global.exception.ErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * 도메인 Repository가 상속한다.
 * {@code rmaRepository.findByIdOrThrow(id, RmaErrorCode.RMA_NOT_FOUND)}
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    default T findByIdOrThrow(ID id, ErrorCode errorCode) {
        return findById(id).orElseThrow(() -> new ApiException(errorCode));
    }
}
