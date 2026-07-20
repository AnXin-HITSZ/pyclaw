package com.claw.saas.runtime.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for audit logging. Logs to SLF4J until claw-service integration is implemented.
 */
@Component
public class AuditLogClient {
    private static final Logger log = LoggerFactory.getLogger("com.claw.saas.runtime.audit");

    public void record(String actorType, String actorId, String action, String resourceType, String resourceId, boolean success, String errorMessage) {
        log.info("audit actorType={} actorId={} action={} resourceType={} resourceId={} success={} error={}",
                actorType, actorId, action, resourceType, resourceId, success, errorMessage);
    }
}
