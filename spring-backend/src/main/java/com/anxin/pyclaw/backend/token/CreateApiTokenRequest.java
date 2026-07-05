package com.anxin.pyclaw.backend.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateApiTokenRequest(
        @NotBlank String name,
        OffsetDateTime expiresAt,
        @NotEmpty List<String> scopes
) {
}
