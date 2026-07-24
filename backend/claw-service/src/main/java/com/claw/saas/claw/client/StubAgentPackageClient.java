package com.claw.saas.claw.client;

import com.claw.saas.claw.dto.AgentPackageInfo;
import com.claw.saas.claw.dto.AgentPackageVersionInfo;
import org.springframework.stereotype.Component;

/**
 * No-op stub for agent-marketplace-service package queries.
 * Replaced with a real HTTP client once the agent marketplace is implemented.
 */
@Component
public class StubAgentPackageClient implements AgentPackageClient {

    @Override
    public AgentPackageInfo getPackage(String id) {
        throw new UnsupportedOperationException("agent-marketplace-service not yet integrated");
    }

    @Override
    public AgentPackageVersionInfo getVersion(String versionId) {
        throw new UnsupportedOperationException("agent-marketplace-service not yet integrated");
    }
}
