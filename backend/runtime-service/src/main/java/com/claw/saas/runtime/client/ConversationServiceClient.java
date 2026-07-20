package com.claw.saas.runtime.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub for conversation-service calls (conversation CRUD, messages).
 * TODO: Implement HTTP client to claw-service REST API.
 */
@Component
public class ConversationServiceClient {
    private static final Logger log = LoggerFactory.getLogger(ConversationServiceClient.class);

    public boolean conversationBelongsToClaw(String conversationId, String clawId) {
        log.warn("ConversationServiceClient.conversationBelongsToClaw is a stub — always returns true");
        return true;
    }

    public String getConversationOwnerUserId(String conversationId) {
        log.warn("ConversationServiceClient.getConversationOwnerUserId is a stub");
        return null;
    }
}
