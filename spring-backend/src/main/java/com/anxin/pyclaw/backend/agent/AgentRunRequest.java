package com.anxin.pyclaw.backend.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentRunRequest(
        @NotBlank String prompt,
        String provider,
        String sessionId,
        String toolProfile,
        String model
) {
}
