package com.anxin.pyclaw.backend.pyclaw;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PyclawAgentRunRequest(
        String prompt,
        String provider,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("tool_profile") String toolProfile,
        String model,
        @JsonProperty("api_mode") String apiMode,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("api_key") String apiKey,
        String system,
        @JsonProperty("tools_allow") List<String> toolsAllow,
        @JsonProperty("tools_deny") List<String> toolsDeny,
        @JsonProperty("tools_also_allow") List<String> toolsAlsoAllow,
        @JsonProperty("shell_approval") String shellApproval,
        @JsonProperty("claw_id") String clawId,
        @JsonProperty("owner_user_id") String ownerUserId,
        @JsonProperty("claw_name") String clawName,
        @JsonProperty("role_key") String roleKey,
        @JsonProperty("agent_key") String agentKey,
        @JsonProperty("sandbox_base_url") String sandboxBaseUrl,
        @JsonProperty("workspace_mode") String workspaceMode
) {
    /** Backward-compatible constructor without Claw context fields. */
    public PyclawAgentRunRequest(
            String prompt, String provider, String sessionId, String toolProfile,
            String model, String apiMode, String baseUrl, String apiKey
    ) {
        this(prompt, provider, sessionId, toolProfile, model, apiMode, baseUrl, apiKey,
                null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
