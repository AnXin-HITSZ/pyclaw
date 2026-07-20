package com.claw.saas.claw.client;

import com.claw.saas.claw.dto.AgentPackageInfo;
import com.claw.saas.claw.dto.AgentPackageVersionInfo;

/**
 * Stub for agent-marketplace-service package queries.
 */
public interface AgentPackageClient {
    AgentPackageInfo getPackage(String id);
    AgentPackageVersionInfo getVersion(String versionId);
}
