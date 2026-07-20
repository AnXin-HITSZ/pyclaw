package com.clawsaas.runtime.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for claw-service agent install approval calls.
 * TODO: Implement HTTP client to claw-service REST API.
 */
@Component
public class AgentInstallClient {
    private static final Logger log = LoggerFactory.getLogger(AgentInstallClient.class);

    public String createApproval(String clawId, String ownerUserId, String packageVersionId,
                                  String requestingAgentInstanceId, String reason) {
        log.warn("AgentInstallClient.createApproval is a stub");
        return java.util.UUID.randomUUID().toString();
    }
}
