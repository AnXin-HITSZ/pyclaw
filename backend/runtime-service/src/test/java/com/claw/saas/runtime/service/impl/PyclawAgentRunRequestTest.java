package com.claw.saas.runtime.service.impl;

import com.claw.saas.runtime.dto.PyclawAgentRunRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PyclawAgentRunRequestTest {

    @Test
    void compactConstructorShouldSetDefaultsForNullFields() {
        PyclawAgentRunRequest request = new PyclawAgentRunRequest(
                "test prompt", "openai", "session-1", null, null, null, null, null
        );
        assertEquals("minimal", request.toolProfile());
        assertEquals("auto", request.apiMode());
        assertNotNull(request.system());
        assertNotNull(request.toolsAllow());
        assertNotNull(request.toolsDeny());
        assertNotNull(request.toolsAlsoAllow());
        assertTrue(request.toolsAllow().isEmpty());
    }

    @Test
    void compactConstructorShouldNotOverrideExplicitValues() {
        PyclawAgentRunRequest request = new PyclawAgentRunRequest(
                "test prompt", "openai", "session-1", "coding", "gpt-4",
                "openai", "https://api.openai.com", "sk-test"
        );
        assertEquals("coding", request.toolProfile());
        assertEquals("gpt-4", request.model());
        assertEquals("openai", request.apiMode());
        assertEquals("https://api.openai.com", request.baseUrl());
        assertEquals("sk-test", request.apiKey());
    }

    @Test
    void fullConstructorShouldPreserveAllFields() {
        PyclawAgentRunRequest request = new PyclawAgentRunRequest(
                "prompt", "provider", "sess", "profile", "model",
                "mode", "url", "key", "system-prompt",
                List.of("tool-a"), List.of("tool-b"), List.of("tool-c"),
                "claw-1", "user-1", "claw-name", "role-1", "agent-key",
                "http://sandbox", "conv-1", "agent-inst-1"
        );
        assertEquals("prompt", request.prompt());
        assertEquals("provider", request.provider());
        assertEquals("claw-1", request.clawId());
        assertEquals("user-1", request.ownerUserId());
        assertEquals("conv-1", request.conversationId());
        assertEquals(1, request.toolsAlsoAllow().size());
        assertEquals("tool-c", request.toolsAlsoAllow().get(0));
    }
}
