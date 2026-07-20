package com.claw.saas.runtime.client;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for billing-service usage recording.
 * TODO: Implement HTTP client to billing-service REST API.
 */
@Component
public class UsageClient {
    private static final Logger log = LoggerFactory.getLogger(UsageClient.class);

    public void recordUsage(String userId, String sessionId, String provider, String model,
                            Map<String, Object> message, long latencyMs, boolean success) {
        log.info("usage stub userId={} sessionId={} provider={} model={} latencyMs={} success={}",
                userId, sessionId, provider, model, latencyMs, success);
    }
}
