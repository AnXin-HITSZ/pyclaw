package com.claw.saas.billing.repository;

import com.claw.saas.billing.entity.UsageRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, String> {
}
