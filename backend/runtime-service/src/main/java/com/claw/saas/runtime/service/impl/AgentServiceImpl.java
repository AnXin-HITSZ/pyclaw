package com.claw.saas.runtime.service.impl;

import com.claw.saas.runtime.client.AuditLogClient;
import com.claw.saas.runtime.client.SessionServiceClient;
import com.claw.saas.runtime.client.UsageClient;
import com.claw.saas.runtime.domain.AuthenticatedPrincipal;
import com.claw.saas.runtime.dto.AgentRunRequest;
import com.claw.saas.runtime.dto.AgentRunResponse;
import com.claw.saas.runtime.dto.PyclawAgentRunRequest;
import com.claw.saas.runtime.dto.PyclawAgentRunResponse;
import com.claw.saas.runtime.entity.ProviderConfigEntity;
import com.claw.saas.runtime.exception.ApiException;
import com.claw.saas.runtime.repository.ProviderConfigRepository;
import com.claw.saas.runtime.service.AgentService;
import com.claw.saas.runtime.service.ProviderConfigService;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentServiceImpl implements AgentService {
    private final PyclawClient pyclawClient;
    private final AuditLogClient auditLogClient;
    private final UsageClient usageClient;
    private final ProviderConfigRepository providerConfigs;
    private final ProviderConfigService providerConfigService;
    private final SessionServiceClient sessionServiceClient;

    public AgentServiceImpl(
            PyclawClient pyclawClient,
            AuditLogClient auditLogClient,
            UsageClient usageClient,
            ProviderConfigRepository providerConfigs,
            ProviderConfigService providerConfigService,
            SessionServiceClient sessionServiceClient
    ) {
        this.pyclawClient = pyclawClient;
        this.auditLogClient = auditLogClient;
        this.usageClient = usageClient;
        this.providerConfigs = providerConfigs;
        this.providerConfigService = providerConfigService;
        this.sessionServiceClient = sessionServiceClient;
    }

    @Override
    public AgentRunResponse run(AuthenticatedPrincipal principal, AgentRunRequest request) {
        long started = System.nanoTime();
        boolean success = false;

        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            sessionServiceClient.requireOwned(request.sessionId(), principal.userId());
        }

        ProviderConfigEntity providerConfig = resolveProviderConfig(request.provider());
        if (providerConfig != null) {
            requireProviderAccess(providerConfig, principal);
        }

        String provider = providerConfig == null ? defaultProvider(request.provider()) : pyclawProvider(providerConfig.getProviderType());
        String model = request.model() != null && !request.model().isBlank()
                ? request.model()
                : providerConfig == null ? null : providerConfig.getModel();
        String apiKey = providerConfig == null ? null : providerConfigService.getDecryptedApiKey(providerConfig);
        try {
            PyclawAgentRunResponse response = pyclawClient.runAgent(new PyclawAgentRunRequest(
                    request.prompt(),
                    provider,
                    request.sessionId(),
                    request.toolProfile() == null ? "minimal" : request.toolProfile(),
                    model,
                    providerConfig == null ? "auto" : providerConfig.getApiMode(),
                    providerConfig == null ? null : providerConfig.getBaseUrl(),
                    apiKey
            ));
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            success = true;
            usageClient.recordUsage(principal.userId(), response.sessionId(),
                    provider, model, response.message(), latencyMs, true);
            return new AgentRunResponse(response.sessionId(), response.message(), response.text(), latencyMs);
        } finally {
            auditLogClient.record(principal.actorType(), principal.userId(), "agent:run", "session",
                    request.sessionId(), success, success ? null : "agent call failed");
        }
    }

    private void requireProviderAccess(ProviderConfigEntity providerConfig, AuthenticatedPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("user:manage"));
        if (isAdmin) return;
        if (providerConfig.isShared()) return;
        if (!Objects.equals(providerConfig.getOwnerUserId(), principal.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Provider not accessible");
        }
    }

    private ProviderConfigEntity resolveProviderConfig(String requestedProvider) {
        if (requestedProvider == null || requestedProvider.isBlank()) {
            ProviderConfigEntity compatible = providerConfigs.findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc("openai-compatible");
            return compatible == null ? providerConfigs.findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc("openai") : compatible;
        }
        ProviderConfigEntity byName = providerConfigs.findFirstByNameIgnoreCaseAndEnabledTrue(requestedProvider);
        if (byName != null) return byName;
        ProviderConfigEntity byType = providerConfigs.findFirstByProviderTypeIgnoreCaseAndEnabledTrue(requestedProvider);
        if (byType != null) return byType;
        if ("openai".equalsIgnoreCase(requestedProvider)) {
            return providerConfigs.findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc("openai-compatible");
        }
        return null;
    }

    private String defaultProvider(String requestedProvider) {
        return requestedProvider == null || requestedProvider.isBlank() ? "openai" : pyclawProvider(requestedProvider);
    }

    private String pyclawProvider(String providerType) {
        return "openai-compatible".equalsIgnoreCase(providerType) ? "openai" : providerType;
    }
}
