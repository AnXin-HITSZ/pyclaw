package com.clawsaas.runtime.dto;

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
        String ownerUserId,
        boolean shared,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProviderConfigResponse from(com.clawsaas.runtime.entity.ProviderConfigEntity entity) {
        String apiKey = entity.getApiKey();
        boolean configured = apiKey != null && !apiKey.isBlank();
        return new ProviderConfigResponse(
                entity.getId(),
                entity.getName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getModel(),
                entity.getApiMode(),
                entity.getSecretRef(),
                configured,
                configured ? last4(apiKey) : null,
                entity.getOwnerUserId(),
                entity.isShared(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static ProviderConfigResponse from(com.clawsaas.runtime.entity.ProviderConfigEntity entity,
                                               com.clawsaas.runtime.config.SecretEncryptionService encryption) {
        String decrypted = encryption.decrypt(entity.getApiKey());
        boolean configured = decrypted != null && !decrypted.isBlank();
        return new ProviderConfigResponse(
                entity.getId(),
                entity.getName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getModel(),
                entity.getApiMode(),
                entity.getSecretRef(),
                configured,
                configured ? last4(decrypted) : null,
                entity.getOwnerUserId(),
                entity.isShared(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String last4(String value) {
        if (value == null || value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }
}
