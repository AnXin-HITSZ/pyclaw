package com.clawsaas.runtime.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for agent-marketplace-service calls (package/version queries).
 * TODO: Implement HTTP client to agent-marketplace-service REST API.
 */
@Component
public class AgentPackageClient {
    private static final Logger log = LoggerFactory.getLogger(AgentPackageClient.class);

    public boolean isVersionPublished(String packageVersionId) {
        log.warn("AgentPackageClient.isVersionPublished is a stub — always returns true");
        return true;
    }
}
