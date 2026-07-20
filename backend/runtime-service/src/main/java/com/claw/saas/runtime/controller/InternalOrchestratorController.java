package com.claw.saas.runtime.controller;

import com.claw.saas.runtime.dto.*;
import com.claw.saas.runtime.service.OrchestratorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/orchestrator/agents")
public class InternalOrchestratorController {

    private final OrchestratorService orchestrator;

    public InternalOrchestratorController(OrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/discover")
    public List<OrchestratorDiscoverResponse> discover(@Valid @RequestBody OrchestratorDiscoverRequest request,
                                                        Authentication authentication) {
        return orchestrator.discoverAgentsInternal(request, authentication);
    }

    @PostMapping("/install-requests")
    public String createInstallRequest(@Valid @RequestBody OrchestratorInstallRequest request,
                                       Authentication authentication) {
        return orchestrator.createInstallRequestInternal(request, authentication);
    }

    @PostMapping("/call")
    public Object callAgent(@Valid @RequestBody OrchestratorCallRequest request,
                            Authentication authentication) {
        return orchestrator.callAgentInternal(request, authentication);
    }
}
