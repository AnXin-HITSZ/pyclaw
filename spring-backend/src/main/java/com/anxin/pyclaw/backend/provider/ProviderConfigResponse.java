package com.anxin.pyclaw.backend.provider;

import com.anxin.pyclaw.backend.config.SecretEncryptionService;
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
    public static ProviderConfigResponse from(ProviderConfigEntity entity, SecretEncryptionService encryption) {
        boolean hasKey = entity.getApiKey() != null && !entity.getApiKey().isBlank();
        String decrypted = hasKey ? encryption.decrypt(entity.getApiKey()) : null;
        return new ProviderConfigResponse(
                entity.getId(),
                entity.getName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getModel(),
                entity.getApiMode(),
                entity.getSecretRef(),
                hasKey,
                last4(decrypted),
                entity.getOwnerUserId(),
                entity.isShared(),
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
