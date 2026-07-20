package com.claw.saas.agentmarketplace.client;

public interface AgentToolPolicyClient {

    AgentToolPolicyDTO findByAgentId(String agentId);

    record AgentToolPolicyDTO(
            String id,
            String agentId,
            String profile,
            boolean readonly
    ) {}
}
