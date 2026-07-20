package com.claw.saas.claw.repository;

import com.claw.saas.claw.domain.ConversationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    List<ConversationEntity> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId);

    List<ConversationEntity> findByClawIdAndOwnerUserIdOrderByUpdatedAtDesc(String clawId, String ownerUserId);

    Optional<ConversationEntity> findByIdAndOwnerUserId(String id, String ownerUserId);
}
