package com.anxin.pyclaw.backend.session;

import java.time.OffsetDateTime;

public record SessionSummaryResponse(
        String sessionId,
        String clawId,
        String clawName,
        String agentKey,
        String provider,
        String model,
        int messageCount,
        OffsetDateTime createdAt,
        OffsetDateTime lastActiveAt
) {}
