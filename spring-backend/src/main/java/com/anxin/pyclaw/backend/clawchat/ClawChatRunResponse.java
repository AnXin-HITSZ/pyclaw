package com.anxin.pyclaw.backend.clawchat;

import java.util.Map;

public record ClawChatRunResponse(
        String sessionId,
        String clawId,
        String roleKey,
        String agentId,
        String agentKey,
        String text,
        Map<String, Object> message,
        long latencyMs
) {
}
