package com.anxin.pyclaw.backend.auth;

public record LoginResponse(
        String accessToken,
        long expiresIn
) {
}
