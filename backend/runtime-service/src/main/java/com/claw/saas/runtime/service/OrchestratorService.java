package com.claw.saas.runtime.service;

import com.claw.saas.runtime.dto.*;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface OrchestratorService {

    OrchestratorResult resolveTurnAgent(String clawId, String conversationId, String agentInstanceId,
                                         String roleKey, Authentication authentication);

    List<OrchestratorDiscoverResponse> discoverAgents(OrchestratorDiscoverRequest request, Authentication authentication);

    String createInstallRequest(OrchestratorInstallRequest request, Authentication authentication);

    Object callAgent(OrchestratorCallRequest request, Authentication authentication);

    List<OrchestratorDiscoverResponse> discoverAgentsInternal(OrchestratorDiscoverRequest request, Authentication authentication);

    String createInstallRequestInternal(OrchestratorInstallRequest request, Authentication authentication);

    Object callAgentInternal(OrchestratorCallRequest request, Authentication authentication);
}
