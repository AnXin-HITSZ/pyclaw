package com.anxin.pyclaw.backend.token;

public record CreateApiTokenResponse(
        String tokenId,
        String token
) {
}
