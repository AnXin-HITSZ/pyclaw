package com.anxin.pyclaw.backend.token;

import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
public class ApiTokenController {
    private final ApiTokenRepository repository;
    private final ApiTokenService service;

    public ApiTokenController(ApiTokenRepository repository, ApiTokenService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('token:manage_self') or hasAuthority('user:manage')")
    public List<ApiTokenEntity> list() {
        return repository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('token:manage_self') or hasAuthority('user:manage')")
    public CreateApiTokenResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal, @Valid @RequestBody CreateApiTokenRequest request) {
        return service.create(principal, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('token:manage_self') or hasAuthority('user:manage')")
    public void revoke(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable String id) {
        service.revoke(id, principal);
    }
}
