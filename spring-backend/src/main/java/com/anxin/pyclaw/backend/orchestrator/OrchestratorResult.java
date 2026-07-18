package com.anxin.pyclaw.backend.orchestrator;

/**
 * Resolved context returned by the Conversation Orchestrator for a single turn.
 * All fields are stable identifiers — no roleKey-based memory keys.
 */
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
