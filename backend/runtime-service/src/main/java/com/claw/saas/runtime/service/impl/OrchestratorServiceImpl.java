package com.claw.saas.runtime.service.impl;

import com.claw.saas.runtime.client.*;
import com.claw.saas.runtime.domain.AuthenticatedPrincipal;
import com.claw.saas.runtime.dto.*;
import com.claw.saas.runtime.exception.ApiException;
import com.claw.saas.runtime.service.OrchestratorService;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service — the "brain" of runtime-service.
 * Handles agent discovery, install requests, and agent-to-agent calls.
 * <p>
 * Cross-service dependencies (claw, conversation, agent-marketplace) are stubbed
 * via the client/ interfaces. Full HTTP integration is deferred to Phase 2.
 */
@Service
public class OrchestratorServiceImpl implements OrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(OrchestratorServiceImpl.class);

    private final ClawServiceClient clawServiceClient;
    private final ConversationServiceClient conversationServiceClient;
    private final ClawChatServiceClient clawChatServiceClient;
    private final AgentPackageClient agentPackageClient;
    private final AgentInstallClient agentInstallClient;
    private final AuditLogClient auditLogClient;

    public OrchestratorServiceImpl(
            ClawServiceClient clawServiceClient,
            ConversationServiceClient conversationServiceClient,
            ClawChatServiceClient clawChatServiceClient,
            AgentPackageClient agentPackageClient,
            AgentInstallClient agentInstallClient,
            AuditLogClient auditLogClient
    ) {
        this.clawServiceClient = clawServiceClient;
        this.conversationServiceClient = conversationServiceClient;
        this.clawChatServiceClient = clawChatServiceClient;
        this.agentPackageClient = agentPackageClient;
        this.agentInstallClient = agentInstallClient;
        this.auditLogClient = auditLogClient;
    }

    @Override
    public OrchestratorResult resolveTurnAgent(String clawId, String conversationId,
                                                String agentInstanceId, String roleKey,
                                                Authentication authentication) {
        requireOwnedClaw(clawId, authentication);
        conversationServiceClient.conversationBelongsToClaw(conversationId, clawId);
        return new OrchestratorResult(
                conversationId, agentInstanceId, null, null, roleKey,
                "agent-" + agentInstanceId, null, null
        );
    }

    @Override
    public List<OrchestratorDiscoverResponse> discoverAgents(OrchestratorDiscoverRequest request, Authentication authentication) {
        requireOwnedClaw(request.clawId(), authentication);
        log.warn("discoverAgents is stubbed — agent marketplace integration pending");
        return List.of();
    }

    @Override
    public String createInstallRequest(OrchestratorInstallRequest request, Authentication authentication) {
        requireOwnedClaw(request.clawId(), authentication);
        if (!agentPackageClient.isVersionPublished(request.packageVersionId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Package version is not published");
        }
        return agentInstallClient.createApproval(
                request.clawId(), actorId(authentication),
                request.packageVersionId(), request.requestingAgentInstanceId(), request.reason());
    }

    @Override
    public Object callAgent(OrchestratorCallRequest request, Authentication authentication) {
        requireOwnedClaw(request.clawId(), authentication);
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            conversationServiceClient.conversationBelongsToClaw(request.conversationId(), request.clawId());
        }
        ClawChatServiceClient.ClawChatRunRequest chatRequest = new ClawChatServiceClient.ClawChatRunRequest(
                request.message(), request.targetRoleKey(), null,
                request.conversationId(), request.targetAgentInstanceId()
        );
        ClawChatServiceClient.ClawChatRunResponse result = clawChatServiceClient.run(request.clawId(), chatRequest);
        auditInternal(authentication, "agent.call", request.clawId(), request.conversationId(),
                request.callingAgentInstanceId(), null, request.targetAgentInstanceId(), true);
        return result;
    }

    @Override
    public List<OrchestratorDiscoverResponse> discoverAgentsInternal(OrchestratorDiscoverRequest request, Authentication authentication) {
        requireClawExists(request.clawId());
        auditInternal(authentication, "agent.discover", request.clawId(), null, null, null, null, true);
        return discoverAgents(request, authentication);
    }

    @Override
    public String createInstallRequestInternal(OrchestratorInstallRequest request, Authentication authentication) {
        requireClawExists(request.clawId());
        String result = createInstallRequest(request, authentication);
        auditInternal(authentication, "agent_install.request", request.clawId(), null, null,
                request.packageVersionId(), null, true);
        return result;
    }

    @Override
    public Object callAgentInternal(OrchestratorCallRequest request, Authentication authentication) {
        requireClawExists(request.clawId());
        if (!clawServiceClient.isAgentEnabled(request.clawId(), request.callingAgentInstanceId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Calling agent instance not found or disabled");
        }
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            conversationServiceClient.conversationBelongsToClaw(request.conversationId(), request.clawId());
        }
        Object result = callAgent(request, authentication);
        auditInternal(authentication, "agent.call", request.clawId(), request.conversationId(),
                request.callingAgentInstanceId(), null, request.targetAgentInstanceId(), true);
        return result;
    }

    private void requireOwnedClaw(String clawId, Authentication authentication) {
        if (!clawServiceClient.clawExists(clawId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Claw not found");
        }
    }

    private void requireClawExists(String clawId) {
        if (!clawServiceClient.clawExists(clawId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Claw not found");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        Set<String> authorities = authentication == null ? Set.of() : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return authorities.contains("user:manage");
    }

    private String actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private void auditInternal(Authentication authentication, String action, String clawId,
                                String conversationId, String callingAgentInstanceId,
                                String packageVersionId, String targetAgentInstanceId, boolean success) {
        String actorId = actorId(authentication);
        String actorType = authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal
                ? principal.actorType() : "INTERNAL_SERVICE";
        auditLogClient.record(actorType, actorId != null ? actorId : "internal", action,
                "claw", clawId, success, null);
    }
}
