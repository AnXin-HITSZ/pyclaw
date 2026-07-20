package com.claw.saas.claw.repository;

import com.claw.saas.claw.domain.RouteBindingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteBindingRepository extends JpaRepository<RouteBindingEntity, String> {
    List<RouteBindingEntity> findByEnabledTrueOrderByPriorityDescUpdatedAtDesc();
}
