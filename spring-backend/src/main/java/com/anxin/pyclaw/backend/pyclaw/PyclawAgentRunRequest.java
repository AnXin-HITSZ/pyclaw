package com.anxin.pyclaw.backend.pyclaw;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PyclawAgentRunRequest(
        String prompt,
        String provider,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("tool_profile") String toolProfile,
        String model
) {
}
