package com.claw.saas.runtime.service;

import com.claw.saas.runtime.domain.AuthenticatedPrincipal;
import com.claw.saas.runtime.dto.AgentRunRequest;
import com.claw.saas.runtime.dto.AgentRunResponse;

public interface AgentService {
    AgentRunResponse run(AuthenticatedPrincipal principal, AgentRunRequest request);
}
