package com.claw.saas.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SaasClawToolResolveResponse(
        String profile,
        List<SaasClawToolCatalogEntry> tools,
        @JsonProperty("denied_tools") List<SaasClawDeniedTool> deniedTools,
        @JsonProperty("prompt_fragments") List<SaasClawPromptFragment> promptFragments
) {}
