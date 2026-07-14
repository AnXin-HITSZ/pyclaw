package com.anxin.pyclaw.backend.provider;

import com.anxin.pyclaw.backend.audit.AuditLogService;
import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import com.anxin.pyclaw.backend.common.ApiException;
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
public class ProviderConfigService {
    private final ProviderConfigRepository repository;
    private final AuditLogService auditLogService;

    public ProviderConfigService(ProviderConfigRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    public List<ProviderConfigResponse> list(Authentication authentication) {
        List<ProviderConfigEntity> rows = isAdmin(authentication)
                ? repository.findAll()
                : repository.findByOwnerUserIdOrSharedTrueOrderByUpdatedAtDesc(actorId(authentication));
        return rows.stream().map(ProviderConfigResponse::from).toList();
    }

    public List<ProviderOptionResponse> options(Authentication authentication) {
        List<ProviderConfigEntity> rows = isAdmin(authentication)
                ? repository.findAll()
                : repository.findByOwnerUserIdOrSharedTrueOrderByUpdatedAtDesc(actorId(authentication));
        return rows.stream().map(ProviderOptionResponse::from).toList();
    }

    public ProviderConfigResponse get(String id, Authentication authentication) {
        return ProviderConfigResponse.from(requireOwned(id, authentication));
    }

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
        return ProviderConfigResponse.from(saved);
    }

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
        return ProviderConfigResponse.from(saved);
    }

    @Transactional
    public void delete(String id, Authentication authentication) {
        requireOwned(id, authentication);
        repository.deleteById(id);
        audit(authentication, "provider.delete", id, true, null);
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
            entity.setApiKey(request.apiKey().trim());
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
        auditLogService.record(actorType(authentication), actorId(authentication), action, "provider", resourceId, success, error);
    }
}
