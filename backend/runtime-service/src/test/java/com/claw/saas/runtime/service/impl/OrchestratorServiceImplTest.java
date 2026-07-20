package com.claw.saas.runtime.service.impl;

import com.claw.saas.runtime.client.*;
import com.claw.saas.runtime.dto.*;
import com.claw.saas.runtime.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

class OrchestratorServiceImplTest {

    private ClawServiceClient clawServiceClient;
    private ConversationServiceClient conversationServiceClient;
    private ClawChatServiceClient clawChatServiceClient;
    private AgentPackageClient agentPackageClient;
    private AgentInstallClient agentInstallClient;
    private AuditLogClient auditLogClient;
    private OrchestratorServiceImpl service;

    @BeforeEach
    void setUp() {
        // Use stubs directly instead of mocks to avoid Mockito/Java 24 issues
        clawServiceClient = new ClawServiceClient();
        conversationServiceClient = new ConversationServiceClient();
        clawChatServiceClient = new ClawChatServiceClient();
        agentPackageClient = new AgentPackageClient();
        agentInstallClient = new AgentInstallClient();
        auditLogClient = new AuditLogClient();
        service = new OrchestratorServiceImpl(
                clawServiceClient, conversationServiceClient, clawChatServiceClient,
                agentPackageClient, agentInstallClient, auditLogClient
        );
    }

    @Test
    void discoverAgentsShouldNotThrowWhenClawExists() {
        var request = new OrchestratorDiscoverRequest("claw-1", null, null, null);
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
        var result = service.discoverAgents(request, auth);
        assertNotNull(result);
    }

    @Test
    void callAgentShouldSucceedWhenClawExists() {
        var request = new OrchestratorCallRequest("claw-1", "agent-a", "agent-b", null, "hello", null);
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
        Object result = service.callAgent(request, auth);
        assertNotNull(result);
    }

    @Test
    void createInstallRequestShouldSucceed() {
        var request = new OrchestratorInstallRequest("claw-1", "v1", "agent-a", "need this");
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
        String result = service.createInstallRequest(request, auth);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void resolveTurnAgentShouldReturnResult() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");
        var result = service.resolveTurnAgent("claw-1", "conv-1", "agent-1", "assistant", auth);
        assertNotNull(result);
        assertEquals("conv-1", result.conversationId());
        assertEquals("agent-1", result.agentInstanceId());
    }
}
