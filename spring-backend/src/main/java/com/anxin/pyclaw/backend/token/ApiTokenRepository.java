package com.anxin.pyclaw.backend.token;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiTokenEntity, String> {
    Optional<ApiTokenEntity> findByTokenHash(String tokenHash);
    List<ApiTokenEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
