package com.anxin.pyclaw.backend.token;

import com.anxin.pyclaw.backend.auth.AuthUtil;
import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import com.anxin.pyclaw.backend.common.ApiException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ApiTokenService {
    private final ApiTokenRepository repository;

    public ApiTokenService(ApiTokenRepository repository) {
        this.repository = repository;
    }

    public CreateApiTokenResponse create(AuthenticatedPrincipal principal, CreateApiTokenRequest request) {
        String token = AuthUtil.randomToken("pcat_", 32);
        ApiTokenEntity entity = new ApiTokenEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(principal.userId());
        entity.setName(request.name());
        entity.setTokenHash(AuthUtil.sha256(token));
        entity.setScopes(String.join(",", request.scopes()));
        entity.setExpiresAt(request.expiresAt());
        entity.setCreatedAt(OffsetDateTime.now());
        repository.save(entity);
        return new CreateApiTokenResponse(entity.getId(), token);
    }

    public ApiTokenEntity requireActiveToken(String rawToken) {
        ApiTokenEntity entity = repository.findByTokenHash(AuthUtil.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid API token"));
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.getRevokedAt() != null || (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(now))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "API token expired or revoked");
        }
        entity.setLastUsedAt(now);
        return repository.save(entity);
    }

    public void revoke(String id, AuthenticatedPrincipal principal) {
        ApiTokenEntity entity = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "API token not found"));
        if (!principal.userId().equals(entity.getUserId()) && principal.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("user:manage"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cannot revoke this token");
        }
        entity.setRevokedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
