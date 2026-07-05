package com.anxin.pyclaw.backend.usage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, String> {
}
