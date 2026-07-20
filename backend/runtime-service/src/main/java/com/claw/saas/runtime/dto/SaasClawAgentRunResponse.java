package com.claw.saas.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record SaasClawAgentRunResponse(
        String status,
        @JsonProperty("session_id") String sessionId,
        Map<String, Object> message,
        String text,
        SaasClawApprovalResponse approval
) {}
