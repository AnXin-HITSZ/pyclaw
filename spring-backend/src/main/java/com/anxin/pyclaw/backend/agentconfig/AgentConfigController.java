package com.anxin.pyclaw.backend.agentconfig;

import com.anxin.pyclaw.backend.agentpackage.AgentPackageService;
import com.anxin.pyclaw.backend.agentpackage.AgentPackageVersionResponse;
import com.anxin.pyclaw.backend.agentpackage.AgentPublishRequest;
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
@RequestMapping("/api/agents")
public class AgentConfigController {
    private final AgentConfigService service;
    private final AgentPackageService packageService;

    public AgentConfigController(AgentConfigService service, AgentPackageService packageService) {
        this.service = service;
        this.packageService = packageService;
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('agent:create')")
    public AgentPackageVersionResponse publish(@PathVariable String id, @Valid @RequestBody AgentPublishRequest request, Authentication authentication) {
        return packageService.publish(id, request, authentication);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:read')")
    public List<AgentConfigResponse> list(Authentication authentication) {
        return service.list(authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:read')")
    public AgentConfigResponse get(@PathVariable String id, Authentication authentication) {
        return service.get(id, authentication);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('agent:create')")
    public AgentConfigResponse create(@Valid @RequestBody AgentConfigRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:update')")
    public AgentConfigResponse update(@PathVariable String id, @Valid @RequestBody AgentConfigRequest request, Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:delete')")
    public void delete(@PathVariable String id, Authentication authentication) {
        service.delete(id, authentication);
    }
}
