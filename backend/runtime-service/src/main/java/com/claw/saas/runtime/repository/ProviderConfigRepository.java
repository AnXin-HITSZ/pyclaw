package com.claw.saas.runtime.repository;

import com.claw.saas.runtime.entity.ProviderConfigEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderConfigRepository extends JpaRepository<ProviderConfigEntity, String> {
    ProviderConfigEntity findFirstByNameIgnoreCaseAndEnabledTrue(String name);
    ProviderConfigEntity findFirstByProviderTypeIgnoreCaseAndEnabledTrue(String providerType);
    ProviderConfigEntity findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc(String providerType);
    List<ProviderConfigEntity> findByOwnerUserIdOrSharedTrueOrderByUpdatedAtDesc(String ownerUserId);
}
