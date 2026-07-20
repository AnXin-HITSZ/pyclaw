package com.claw.saas.claw.repository;

import com.claw.saas.claw.domain.ClawAgentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClawAgentRepository extends JpaRepository<ClawAgentEntity, String> {
    List<ClawAgentEntity> findByClawIdOrderBySortOrderAscCreatedAtAsc(String clawId);

    void deleteByClawId(String clawId);
}
