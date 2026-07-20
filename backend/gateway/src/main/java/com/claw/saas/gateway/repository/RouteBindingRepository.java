package com.claw.saas.gateway.repository;

import com.claw.saas.gateway.entity.RouteBindingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteBindingRepository extends JpaRepository<RouteBindingEntity, String> {
    List<RouteBindingEntity> findByEnabledTrueOrderByPriorityDescUpdatedAtDesc();
}
