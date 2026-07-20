package com.claw.saas.runtime.service.impl;

import com.claw.saas.runtime.client.AuditLogClient;
import com.claw.saas.runtime.config.SecretEncryptionService;
import com.claw.saas.runtime.domain.AuthenticatedPrincipal;
import com.claw.saas.runtime.dto.ProviderConfigRequest;
import com.claw.saas.runtime.dto.ProviderConfigResponse;
import com.claw.saas.runtime.dto.ProviderOptionResponse;
import com.claw.saas.runtime.entity.ProviderConfigEntity;
import com.claw.saas.runtime.exception.ApiException;
import com.claw.saas.runtime.repository.ProviderConfigRepository;
import com.claw.saas.runtime.service.ProviderConfigService;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class ProviderConfigServiceImpl implements ProviderConfigService {
    private final ProviderConfigRepository repository;
    private final AuditLogClient auditLogClient;
    private final SecretEncryptionService encryption;

    public ProviderConfigServiceImpl(ProviderConfigRepository repository, AuditLogClient auditLogClient, SecretEncryptionService encryption) {
        this.repository = repository;
        this.auditLogClient = auditLogClient;
        this.encryption = encryption;
    }

    @Override
    public String getDecryptedApiKey(ProviderConfigEntity entity) {
        return encryption.decrypt(entity.getApiKey());
    }

    @Override
    public List<ProviderConfigResponse> list(Authentication authentication) {
        List<ProviderConfigEntity> rows = isAdmin(authentication)
                ? repository.findAll()
                : repository.findByOwnerUserIdOrSharedTrueOrderByUpdatedAtDesc(actorId(authentication));
        return rows.stream().map(e -> ProviderConfigResponse.from(e, encryption)).toList();
    }

    @Override
    public List<ProviderOptionResponse> options(Authentication authentication) {
        List<ProviderConfigEntity> rows = isAdmin(authentication)
                ? repository.findAll()
                : repository.findByOwnerUserIdOrSharedTrueOrderByUpdatedAtDesc(actorId(authentication));
        return rows.stream()
                .map(e -> new ProviderOptionResponse(e.getId(), e.getName(), e.getProviderType(), e.getModel(), e.getApiMode(), e.isEnabled()))
                .toList();
    }

    @Override
    public ProviderConfigResponse get(String id, Authentication authentication) {
        return ProviderConfigResponse.from(requireOwned(id, authentication), encryption);
    }

    @Override
    @Transactional
    public ProviderConfigResponse create(ProviderConfigRequest request, Authentication authentication) {
        OffsetDateTime now = OffsetDateTime.now();
        ProviderConfigEntity entity = new ProviderConfigEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setOwnerUserId(actorId(authentication));
        entity.setShared(isAdmin(authentication) && request.shared());
        apply(entity, request);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        ProviderConfigEntity saved = repository.save(entity);
        audit(authentication, "provider.create", saved.getId(), true, null);
        return ProviderConfigResponse.from(saved, encryption);
    }

    @Override
    @Transactional
    public ProviderConfigResponse update(String id, ProviderConfigRequest request, Authentication authentication) {
        ProviderConfigEntity entity = requireOwned(id, authentication);
        apply(entity, request);
        if (isAdmin(authentication)) {
            entity.setShared(request.shared());
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        ProviderConfigEntity saved = repository.save(entity);
        audit(authentication, "provider.update", saved.getId(), true, null);
        return ProviderConfigResponse.from(saved, encryption);
    }

    @Override
    @Transactional
    public void delete(String id, Authentication authentication) {
        requireOwned(id, authentication);
        repository.deleteById(id);
        audit(authentication, "provider.delete", id, true, null);
    }

    @Override
    public ProviderConfigEntity resolveForAgentAndUser(String agentProviderId, String agentProvider, String ownerUserId) {
        // 1. Direct providerId reference
        if (agentProviderId != null && !agentProviderId.isBlank()) {
            ProviderConfigEntity byId = repository.findById(agentProviderId).orElse(null);
            if (byId != null && byId.isEnabled() && (byId.isShared() || Objects.equals(byId.getOwnerUserId(), ownerUserId))) {
                return byId;
            }
        }

        // 2. By name (owned first, then shared)
        if (agentProvider != null && !agentProvider.isBlank()) {
            ProviderConfigEntity byName = repository.findFirstByNameIgnoreCaseAndEnabledTrue(agentProvider);
            if (byName != null && (byName.isShared() || Objects.equals(byName.getOwnerUserId(), ownerUserId))) {
                return byName;
            }
            ProviderConfigEntity byNameShared = repository
                    .findByOwnerUserIdOrSharedTrueOrderByUpdatedAtDesc(ownerUserId).stream()
                    .filter(p -> p.isEnabled() && agentProvider.equalsIgnoreCase(p.getName()))
                    .findFirst().orElse(null);
            if (byNameShared != null) return byNameShared;
        }

        // 3. By provider type (owned first, then shared)
        String providerType = agentProvider;
        if (providerType == null || providerType.isBlank()) {
            providerType = "openai-compatible";
        }
        String normalizedType = providerType.trim().toLowerCase();
        ProviderConfigEntity byTypeOwned = repository.findAll().stream()
                .filter(p -> p.isEnabled() && normalizedType.equals(p.getProviderType().toLowerCase())
                        && Objects.equals(p.getOwnerUserId(), ownerUserId))
                .findFirst().orElse(null);
        if (byTypeOwned != null) return byTypeOwned;

        ProviderConfigEntity byTypeShared = repository.findAll().stream()
                .filter(p -> p.isEnabled() && p.isShared()
                        && normalizedType.equals(p.getProviderType().toLowerCase()))
                .findFirst().orElse(null);
        if (byTypeShared != null) return byTypeShared;

        // 4. For openai, also try openai-compatible
        if ("openai".equals(normalizedType)) {
            return repository.findFirstByProviderTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc("openai-compatible");
        }

        return null;
    }

    private ProviderConfigEntity requireOwned(String id, Authentication authentication) {
        ProviderConfigEntity entity = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provider config not found"));
        if (!isAdmin(authentication) && !Objects.equals(entity.getOwnerUserId(), actorId(authentication))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Provider config not found");
        }
        return entity;
    }

    private void apply(ProviderConfigEntity entity, ProviderConfigRequest request) {
        entity.setName(request.name());
        entity.setProviderType(request.providerType());
        entity.setBaseUrl(request.baseUrl());
        entity.setModel(request.model());
        entity.setApiMode(request.apiMode());
        entity.setSecretRef(request.secretRef());
        if (request.clearApiKey()) {
            entity.setApiKey(null);
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            entity.setApiKey(encryption.encrypt(request.apiKey().trim()));
        }
        entity.setEnabled(request.enabled());
    }

    private boolean isAdmin(Authentication authentication) {
        Set<String> authorities = authentication == null ? Set.of() : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return authorities.contains("user:manage");
    }

    private String actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private String actorType(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.actorType();
        }
        return "UNKNOWN";
    }

    private void audit(Authentication authentication, String action, String resourceId, boolean success, String error) {
        auditLogClient.record(actorType(authentication), actorId(authentication), action, "provider", resourceId, success, error);
    }
}
