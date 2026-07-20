package com.clawsaas.billing.repository;

import com.clawsaas.billing.entity.UsageRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, String> {
}
