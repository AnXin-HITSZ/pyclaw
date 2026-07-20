package com.claw.saas.claw.client;

import com.claw.saas.claw.domain.ProviderConfigEntity;

/**
 * Stub for LLM provider configuration queries.
 */
public interface ProviderConfigClient {
    ProviderConfigEntity findById(String id);
    ProviderConfigEntity findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc(String providerType);
    ProviderConfigEntity findFirstByNameIgnoreCaseAndEnabledTrue(String name);
    String getDecryptedApiKey(ProviderConfigEntity provider);
    ProviderConfigEntity resolveForAgentAndUser(Object agent, String ownerUserId);
}
