package com.clawsaas.runtime.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for claw-chat service calls (agent chat run).
 * TODO: Implement HTTP client to claw-service REST API.
 */
@Component
public class ClawChatServiceClient {
    private static final Logger log = LoggerFactory.getLogger(ClawChatServiceClient.class);

    /**
     * Stub for running a chat through the claw-service.
     * Returns a placeholder response.
     */
    public ClawChatRunResponse run(String clawId, ClawChatRunRequest request) {
        log.warn("ClawChatServiceClient.run is a stub — returning placeholder");
        return new ClawChatRunResponse("stub", "Stub response", null, 0);
    }

    public record ClawChatRunRequest(
            String prompt,
            String roleKey,
            String provider,
            String conversationId,
            String agentInstanceId
    ) {}

    public record ClawChatRunResponse(
            String status,
            String text,
            String sessionId,
            long latencyMs
    ) {}
}
