package com.anxin.pyclaw.backend.agentpackage;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-packages")
public class AgentPackageController {
    private final AgentPackageService service;

    public AgentPackageController(AgentPackageService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:read')")
    public List<AgentPackageResponse> list(Authentication authentication) {
        return service.list(authentication);
    }

    @GetMapping("/{packageId}")
    @PreAuthorize("hasAuthority('agent:read')")
    public AgentPackageResponse get(@PathVariable String packageId, Authentication authentication) {
        return service.get(packageId, authentication);
    }

    @GetMapping("/{packageId}/versions")
    @PreAuthorize("hasAuthority('agent:read')")
    public List<AgentPackageVersionResponse> listVersions(@PathVariable String packageId, Authentication authentication) {
        return service.listVersions(packageId, authentication);
    }
}
