package com.claw.saas.gateway.service;

import com.claw.saas.gateway.entity.AgentToolPolicyEntity;

/**
 * Stub interface for AgentConfig domain logic.
 * Full implementation lives in agent-marketplace-service.
 */
public interface AgentConfigService {
    AgentToolPolicyEntity requirePolicy(String agentId);
}
