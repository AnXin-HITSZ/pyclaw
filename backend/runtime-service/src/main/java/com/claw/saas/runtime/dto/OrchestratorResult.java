package com.claw.saas.runtime.dto;

public record OrchestratorResult(
        String conversationId,
        String agentInstanceId,
        String agentId,
        String agentKey,
        String roleKey,
        String displayName,
        String toolProfile,
        String runtimeSessionId
) {}
