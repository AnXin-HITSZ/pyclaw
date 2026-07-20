package com.claw.saas.runtime.service;

import com.claw.saas.runtime.dto.ProviderConfigRequest;
import com.claw.saas.runtime.dto.ProviderConfigResponse;
import com.claw.saas.runtime.dto.ProviderOptionResponse;
import com.claw.saas.runtime.entity.ProviderConfigEntity;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface ProviderConfigService {
    String getDecryptedApiKey(ProviderConfigEntity entity);
    List<ProviderConfigResponse> list(Authentication authentication);
    List<ProviderOptionResponse> options(Authentication authentication);
    ProviderConfigResponse get(String id, Authentication authentication);
    ProviderConfigResponse create(ProviderConfigRequest request, Authentication authentication);
    ProviderConfigResponse update(String id, ProviderConfigRequest request, Authentication authentication);
    void delete(String id, Authentication authentication);
    ProviderConfigEntity resolveForAgentAndUser(String agentProviderId, String agentProvider, String ownerUserId);
}
