package com.claw.saas.runtime.dto;

public record ProviderOptionResponse(
        String id,
        String name,
        String providerType,
        String model,
        String apiMode,
        boolean enabled
) {}
