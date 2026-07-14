package com.anxin.pyclaw.backend.provider;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class ProviderConfigController {
    private final ProviderConfigService service;

    public ProviderConfigController(ProviderConfigService service) {
        this.service = service;
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('provider:manage') or hasAuthority('provider:manage_self') or hasAuthority('agent:read') or hasAuthority('agent:update')")
    public List<ProviderOptionResponse> options(Authentication authentication) {
        return service.options(authentication);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('provider:manage') or hasAuthority('provider:manage_self') or hasAuthority('agent:run')")
    public List<ProviderConfigResponse> list(Authentication authentication) {
        return service.list(authentication);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('provider:manage') or hasAuthority('provider:manage_self')")
    public ProviderConfigResponse create(@Valid @RequestBody ProviderConfigRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('provider:manage') or hasAuthority('provider:manage_self')")
    public ProviderConfigResponse update(@PathVariable String id, @Valid @RequestBody ProviderConfigRequest request, Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('provider:manage') or hasAuthority('provider:manage_self')")
    public void delete(@PathVariable String id, Authentication authentication) {
        service.delete(id, authentication);
    }
}
