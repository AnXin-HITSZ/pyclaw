package com.clawsaas.runtime.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record UserSecretRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String scope,
        String clawId,
        Map<String, String> values
) {}
