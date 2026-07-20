package com.clawsaas.runtime.service;

import com.clawsaas.runtime.domain.AuthenticatedPrincipal;
import com.clawsaas.runtime.dto.AgentRunRequest;
import com.clawsaas.runtime.dto.AgentRunResponse;

public interface AgentService {
    AgentRunResponse run(AuthenticatedPrincipal principal, AgentRunRequest request);
}
