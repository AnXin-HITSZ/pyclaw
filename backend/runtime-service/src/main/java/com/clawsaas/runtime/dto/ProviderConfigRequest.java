package com.clawsaas.runtime.dto;

import jakarta.validation.constraints.NotBlank;

public record ProviderConfigRequest(
        @NotBlank String name,
        @NotBlank String providerType,
        String baseUrl,
        @NotBlank String model,
        @NotBlank String apiMode,
        String secretRef,
        String apiKey,
        boolean clearApiKey,
        boolean shared,
        boolean enabled
) {}
