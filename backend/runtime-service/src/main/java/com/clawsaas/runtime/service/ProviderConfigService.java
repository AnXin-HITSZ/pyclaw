package com.clawsaas.runtime.service;

import com.clawsaas.runtime.dto.ProviderConfigRequest;
import com.clawsaas.runtime.dto.ProviderConfigResponse;
import com.clawsaas.runtime.dto.ProviderOptionResponse;
import com.clawsaas.runtime.entity.ProviderConfigEntity;
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
