package com.anxin.pyclaw.backend.agent;

import com.anxin.pyclaw.backend.audit.AuditLogService;
import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import com.anxin.pyclaw.backend.common.ApiException;
import com.anxin.pyclaw.backend.provider.ProviderConfigEntity;
import com.anxin.pyclaw.backend.provider.ProviderConfigRepository;
import com.anxin.pyclaw.backend.provider.ProviderConfigService;
import com.anxin.pyclaw.backend.session.SessionService;
import com.anxin.pyclaw.backend.pyclaw.PyclawAgentRunRequest;
import com.anxin.pyclaw.backend.pyclaw.PyclawAgentRunResponse;
import com.anxin.pyclaw.backend.pyclaw.PyclawClient;
import com.anxin.pyclaw.backend.usage.UsageRecordEntity;
import com.anxin.pyclaw.backend.usage.UsageRecordRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
    private final PyclawClient pyclawClient;
    private final AuditLogService auditLogService;
    private final UsageRecordRepository usageRecords;
    private final ProviderConfigRepository providerConfigs;
    private final ProviderConfigService providerConfigService;
    private final SessionService sessionService;

    public AgentService(
            PyclawClient pyclawClient,
            AuditLogService auditLogService,
            UsageRecordRepository usageRecords,
            ProviderConfigRepository providerConfigs,
            ProviderConfigService providerConfigService,
            SessionService sessionService
    ) {
        this.pyclawClient = pyclawClient;
        this.auditLogService = auditLogService;
        this.usageRecords = usageRecords;
        this.providerConfigs = providerConfigs;
        this.providerConfigService = providerConfigService;
        this.sessionService = sessionService;
    }

    public AgentRunResponse run(AuthenticatedPrincipal principal, AgentRunRequest request) {
        long started = System.nanoTime();
        boolean success = false;

        // Validate session ownership if sessionId is provided
        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            sessionService.requireOwned(request.sessionId(), principal);
        }

        ProviderConfigEntity providerConfig = resolveProviderConfig(request.provider());
        // Validate provider ownership: user must own the provider or it must be shared
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
            recordUsage(principal.userId(), response, latencyMs, true);
            return new AgentRunResponse(response.sessionId(), response.message(), response.text(), latencyMs);
        } finally {
            auditLogService.record(principal.actorType(), principal.userId(), "agent:run", "session", request.sessionId(), success, success ? null : "agent call failed");
        }
    }

    private void requireProviderAccess(ProviderConfigEntity providerConfig, AuthenticatedPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("user:manage"));
        if (isAdmin) {
            return;
        }
        if (providerConfig.isShared()) {
            return;
        }
        if (!Objects.equals(providerConfig.getOwnerUserId(), principal.userId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Provider not accessible");
        }
    }

    @SuppressWarnings("unchecked")
    private void recordUsage(String userId, PyclawAgentRunResponse response, long latencyMs, boolean success) {
        UsageRecordEntity entity = new UsageRecordEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setSessionId(response.sessionId());
        if (response.message() != null) {
            entity.setProvider((String) response.message().get("provider"));
            entity.setModel((String) response.message().get("model"));
            Object usage = response.message().get("usage");
            if (usage instanceof Map<?, ?> usageMap) {
                entity.setPromptTokens(numberValue(usageMap.get("prompt_tokens")));
                entity.setCompletionTokens(numberValue(usageMap.get("completion_tokens")));
                entity.setTotalTokens(numberValue(usageMap.get("total_tokens")));
            }
        }
        entity.setSuccess(success);
        entity.setLatencyMs(latencyMs);
        entity.setCreatedAt(OffsetDateTime.now());
        usageRecords.save(entity);
    }

    private Long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private ProviderConfigEntity resolveProviderConfig(String requestedProvider) {
        if (requestedProvider == null || requestedProvider.isBlank()) {
            ProviderConfigEntity compatible = providerConfigs.findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc("openai-compatible");
            return compatible == null ? providerConfigs.findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc("openai") : compatible;
        }
        ProviderConfigEntity byName = providerConfigs.findFirstByNameIgnoreCaseAndEnabledTrue(requestedProvider);
        if (byName != null) {
            return byName;
        }
        ProviderConfigEntity byType = providerConfigs.findFirstByProviderTypeIgnoreCaseAndEnabledTrue(requestedProvider);
        if (byType != null) {
            return byType;
        }
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
