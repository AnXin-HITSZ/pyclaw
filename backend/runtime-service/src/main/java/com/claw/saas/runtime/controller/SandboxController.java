package com.claw.saas.runtime.controller;

import com.claw.saas.runtime.client.ClawServiceClient;
import com.claw.saas.runtime.domain.AuthenticatedPrincipal;
import com.claw.saas.runtime.dto.SandboxWriteFileRequest;
import com.claw.saas.runtime.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for sandbox-runner proxy operations (workspace, files, health).
 * All paths are prefixed with /api/claws/{clawId}/sandbox.
 */
@RestController
@RequestMapping("/api/claws/{clawId}/sandbox")
public class SandboxController {
    private static final Logger log = LoggerFactory.getLogger(SandboxController.class);
    private static final int MAX_FILE_BYTES = 1_048_576;

    private final ClawServiceClient clawServiceClient;
    private final ObjectMapper objectMapper;

    public SandboxController(ClawServiceClient clawServiceClient, ObjectMapper objectMapper) {
        this.clawServiceClient = clawServiceClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/healthz")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> healthz(@PathVariable String clawId, Authentication authentication) {
        requireOwnedClaw(clawId, authentication);
        return ResponseEntity.ok(Map.of("status", "ok", "clawId", clawId));
    }

    @GetMapping("/workspace")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> workspace(@PathVariable String clawId, Authentication authentication) {
        requireOwnedClaw(clawId, authentication);
        return ResponseEntity.ok(Map.of("message", "Sandbox workspace endpoint — stubbed"));
    }

    @GetMapping("/files")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> listFiles(@PathVariable String clawId,
                                        @RequestParam(defaultValue = ".") String path,
                                        Authentication authentication) {
        requireOwnedClaw(clawId, authentication);
        return ResponseEntity.ok(Map.of("message", "Sandbox files endpoint — stubbed", "path", path));
    }

    @GetMapping("/files/{*filePath}")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> getFile(@PathVariable String clawId,
                                      @PathVariable String filePath,
                                      Authentication authentication) {
        requireOwnedClaw(clawId, authentication);
        if (filePath == null || filePath.length() > MAX_FILE_BYTES) {
            return jsonError(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds 1 MiB limit");
        }
        return ResponseEntity.ok(Map.of("message", "Sandbox get file — stubbed", "path", filePath));
    }

    @PutMapping("/files/{*filePath}")
    @PreAuthorize("hasAuthority('claw:update')")
    public ResponseEntity<?> putFile(@PathVariable String clawId,
                                      @PathVariable String filePath,
                                      @RequestBody SandboxWriteFileRequest request,
                                      Authentication authentication) {
        requireOwnedClaw(clawId, authentication);
        return ResponseEntity.ok(Map.of("message", "Sandbox put file — stubbed", "path", filePath));
    }

    private void requireOwnedClaw(String clawId, Authentication authentication) {
        boolean admin = isAdmin(authentication);
        String userId = actorId(authentication);
        if (!admin) {
            String ownerUserId = clawServiceClient.getClawOwnerUserId(clawId);
            if (!Objects.equals(ownerUserId, userId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Claw not found");
            }
        }
    }

    private boolean isAdmin(Authentication authentication) {
        Set<String> authorities = authentication == null ? Set.of() : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return authorities.contains("user:manage");
    }

    private String actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private ResponseEntity<Map<String, String>> jsonError(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", String.valueOf(status.value()), "message", message));
    }
}
