package com.clawsaas.runtime.dto;

public record OrchestratorDiscoverResponse(
        String packageId,
        String packageVersionId,
        String packageKey,
        String name,
        String summary,
        String version,
        String defaultProfile,
        String capabilities
) {}
