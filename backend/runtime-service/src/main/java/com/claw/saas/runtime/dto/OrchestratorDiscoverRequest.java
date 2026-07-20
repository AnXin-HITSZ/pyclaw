package com.claw.saas.runtime.dto;

import jakarta.validation.constraints.NotBlank;

public record OrchestratorDiscoverRequest(
        @NotBlank String clawId,
        String query,
        String capabilities,
        String requiredProfile
) {}
