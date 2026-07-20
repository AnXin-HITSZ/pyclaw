package com.claw.saas.gateway.dto;

public record LoginResponse(
        String accessToken,
        long expiresIn
) {
}
