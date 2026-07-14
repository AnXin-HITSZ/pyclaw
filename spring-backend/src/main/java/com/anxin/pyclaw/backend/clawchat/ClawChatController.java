package com.anxin.pyclaw.backend.clawchat;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claws/{clawId}/chat")
public class ClawChatController {
    private final ClawChatService service;

    public ClawChatController(ClawChatService service) {
        this.service = service;
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('agent:run')")
    public ClawChatRunResponse run(
            @PathVariable String clawId,
            @Valid @RequestBody ClawChatRunRequest request,
            Authentication authentication) {
        return service.run(clawId, request, authentication);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('claw:read')")
    public List<ClawChatSessionResponse> listSessions(
            @PathVariable String clawId,
            Authentication authentication) {
        return service.listSessions(clawId, authentication);
    }
}
