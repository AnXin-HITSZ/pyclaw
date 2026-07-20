package com.clawsaas.runtime.repository;

import com.clawsaas.runtime.entity.ToolApprovalRequestEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolApprovalRequestRepository extends JpaRepository<ToolApprovalRequestEntity, String> {
    Optional<ToolApprovalRequestEntity> findByIdAndClawIdAndOwnerUserId(String id, String clawId, String ownerUserId);
    List<ToolApprovalRequestEntity> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);
    List<ToolApprovalRequestEntity> findByClawIdOrderByCreatedAtDesc(String clawId);
}
