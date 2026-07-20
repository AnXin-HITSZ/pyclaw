package com.claw.saas.runtime.controller;

import com.claw.saas.runtime.dto.*;
import com.claw.saas.runtime.service.OrchestratorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/agents")
public class OrchestratorController {

    private final OrchestratorService orchestrator;

    public OrchestratorController(OrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/discover")
    @PreAuthorize("hasAuthority('agent:read')")
    public List<OrchestratorDiscoverResponse> discover(@Valid @RequestBody OrchestratorDiscoverRequest request,
                                                        Authentication authentication) {
        return orchestrator.discoverAgents(request, authentication);
    }

    @PostMapping("/install-requests")
    @PreAuthorize("hasAuthority('agent:run')")
    public String createInstallRequest(@Valid @RequestBody OrchestratorInstallRequest request,
                                       Authentication authentication) {
        return orchestrator.createInstallRequest(request, authentication);
    }

    @PostMapping("/call")
    @PreAuthorize("hasAuthority('agent:run')")
    public Object callAgent(@Valid @RequestBody OrchestratorCallRequest request,
                            Authentication authentication) {
        return orchestrator.callAgent(request, authentication);
    }
}
