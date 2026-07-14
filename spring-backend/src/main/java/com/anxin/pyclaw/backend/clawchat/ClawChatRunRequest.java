package com.anxin.pyclaw.backend.clawchat;

import jakarta.validation.constraints.NotBlank;

public record ClawChatRunRequest(
        @NotBlank String prompt,
        String roleKey,
        String sessionId
) {
}
