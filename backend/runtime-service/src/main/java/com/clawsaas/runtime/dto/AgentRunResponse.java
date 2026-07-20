package com.clawsaas.runtime.dto;

import java.util.Map;

public record AgentRunResponse(
        String sessionId,
        Map<String, Object> message,
        String text,
        long latencyMs
) {}
