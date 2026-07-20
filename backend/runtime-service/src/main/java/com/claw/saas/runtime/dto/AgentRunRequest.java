package com.claw.saas.runtime.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentRunRequest(
        @NotBlank String prompt,
        String provider,
        String sessionId,
        String toolProfile,
        String model
) {}
