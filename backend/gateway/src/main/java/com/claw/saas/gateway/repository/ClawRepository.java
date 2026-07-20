package com.claw.saas.gateway.repository;

import com.claw.saas.gateway.entity.ClawEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stub repository for Claw.
 * Full repository lives in runtime-service.
 */
public interface ClawRepository extends JpaRepository<ClawEntity, String> {
}
