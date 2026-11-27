package com.u1_2.effect.user.domain;

import com.u1_2.effect.global.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends BaseRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus approvalStatus);
}
