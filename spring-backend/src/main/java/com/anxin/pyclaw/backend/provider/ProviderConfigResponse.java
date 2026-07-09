package com.anxin.pyclaw.backend.provider;

import java.time.OffsetDateTime;

public record ProviderConfigResponse(
        String id,
        String name,
        String providerType,
        String baseUrl,
        String model,
        String apiMode,
        String secretRef,
        boolean apiKeyConfigured,
        String apiKeyLast4,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProviderConfigResponse from(ProviderConfigEntity entity) {
        return new ProviderConfigResponse(
                entity.getId(),
                entity.getName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getModel(),
                entity.getApiMode(),
                entity.getSecretRef(),
                entity.getApiKey() != null && !entity.getApiKey().isBlank(),
                last4(entity.getApiKey()),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String last4(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
    }
}
