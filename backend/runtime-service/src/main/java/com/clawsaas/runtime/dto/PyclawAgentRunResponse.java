package com.clawsaas.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record PyclawAgentRunResponse(
        String status,
        @JsonProperty("session_id") String sessionId,
        Map<String, Object> message,
        String text,
        PyclawApprovalResponse approval
) {}
