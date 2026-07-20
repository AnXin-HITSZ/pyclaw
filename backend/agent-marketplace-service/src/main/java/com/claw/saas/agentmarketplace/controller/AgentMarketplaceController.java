package com.claw.saas.agentmarketplace.controller;

import com.claw.saas.agentmarketplace.dto.AgentPackageResponse;
import com.claw.saas.agentmarketplace.dto.AgentPackageVersionResponse;
import com.claw.saas.agentmarketplace.dto.AgentPublishRequest;
import com.claw.saas.agentmarketplace.service.AgentMarketplaceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-packages")
public class AgentMarketplaceController {
    private final AgentMarketplaceService service;

    public AgentMarketplaceController(AgentMarketplaceService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentPackageResponse> list(Authentication authentication) {
        return service.list(authentication);
    }

    @GetMapping("/{packageId}")
    public AgentPackageResponse get(@PathVariable String packageId, Authentication authentication) {
        return service.get(packageId, authentication);
    }

    @PostMapping("/{agentId}/publish")
    public AgentPackageVersionResponse publish(@PathVariable String agentId,
                                                @Valid @RequestBody AgentPublishRequest request,
                                                Authentication authentication) {
        return service.publish(agentId, request, authentication);
    }

    @GetMapping("/{packageId}/versions")
    public List<AgentPackageVersionResponse> listVersions(@PathVariable String packageId, Authentication authentication) {
        return service.listVersions(packageId, authentication);
    }
}
