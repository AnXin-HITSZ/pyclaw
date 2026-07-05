package com.anxin.pyclaw.backend.auth;

import java.util.List;

public record MeResponse(
        String userId,
        String username,
        String actorType,
        List<String> authorities
) {
}
