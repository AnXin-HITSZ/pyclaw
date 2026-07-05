package com.anxin.pyclaw.backend.user;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        String displayName,
        String authorities
) {
}
