package com.anxin.pyclaw.backend.agent;

import com.anxin.pyclaw.backend.audit.AuditLogService;
import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import com.anxin.pyclaw.backend.pyclaw.PyclawAgentRunRequest;
import com.anxin.pyclaw.backend.pyclaw.PyclawAgentRunResponse;
import com.anxin.pyclaw.backend.pyclaw.PyclawClient;
import com.anxin.pyclaw.backend.usage.UsageRecordEntity;
import com.anxin.pyclaw.backend.usage.UsageRecordRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
    private final PyclawClient pyclawClient;
    private final AuditLogService auditLogService;
    private final UsageRecordRepository usageRecords;

    public AgentService(PyclawClient pyclawClient, AuditLogService auditLogService, UsageRecordRepository usageRecords) {
        this.pyclawClient = pyclawClient;
        this.auditLogService = auditLogService;
        this.usageRecords = usageRecords;
    }

    public AgentRunResponse run(AuthenticatedPrincipal principal, AgentRunRequest request) {
        long started = System.nanoTime();
        boolean success = false;
        try {
            PyclawAgentRunResponse response = pyclawClient.runAgent(new PyclawAgentRunRequest(
                    request.prompt(),
                    request.provider() == null ? "openai" : request.provider(),
                    request.sessionId(),
                    request.toolProfile() == null ? "minimal" : request.toolProfile(),
                    request.model()
            ));
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            success = true;
            recordUsage(principal.userId(), response, latencyMs, true);
            return new AgentRunResponse(response.sessionId(), response.message(), response.text(), latencyMs);
        } finally {
            auditLogService.record(principal.actorType(), principal.userId(), "agent:run", "session", request.sessionId(), success, success ? null : "agent call failed");
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
}
