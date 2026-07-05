package com.anxin.pyclaw.backend.pyclaw;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record PyclawAgentRunResponse(
        @JsonProperty("session_id") String sessionId,
        Map<String, Object> message,
        String text
) {
}
