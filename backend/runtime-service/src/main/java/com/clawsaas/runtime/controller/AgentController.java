package com.clawsaas.runtime.controller;

import com.clawsaas.runtime.domain.AuthenticatedPrincipal;
import com.clawsaas.runtime.dto.AgentRunRequest;
import com.clawsaas.runtime.dto.AgentRunResponse;
import com.clawsaas.runtime.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('agent:run')")
    public AgentRunResponse run(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                 @Valid @RequestBody AgentRunRequest request) {
        return agentService.run(principal, request);
    }
}
