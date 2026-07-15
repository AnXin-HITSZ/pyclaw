package com.anxin.pyclaw.backend.sandbox;

import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import com.anxin.pyclaw.backend.claw.ClawEntity;
import com.anxin.pyclaw.backend.claw.ClawRepository;
import com.anxin.pyclaw.backend.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claws/{clawId}/sandbox")
public class SandboxController {
    private static final Logger log = LoggerFactory.getLogger(SandboxController.class);
    private static final int MAX_FILE_BYTES = 1_048_576;

    private final ClawRepository claws;
    private final SandboxClient sandboxClient;
    private final ObjectMapper objectMapper;

    public SandboxController(ClawRepository claws, SandboxClient sandboxClient, ObjectMapper objectMapper) {
        this.claws = claws;
        this.sandboxClient = sandboxClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/healthz")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> healthz(@PathVariable String clawId, Authentication authentication) {
        ClawEntity claw = requireOwned(clawId, authentication);
        try {
            String result = sandboxClient.healthz(claw.getOwnerUserId(), clawId);
            return jsonResponse(parseJson(result));
        } catch (SandboxClient.SandboxClientException e) {
            log.warn("sandbox healthz failed: claw_id={} error={}", clawId, e.getMessage());
            return jsonError(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @GetMapping("/workspace")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> workspace(@PathVariable String clawId, Authentication authentication) {
        ClawEntity claw = requireOwned(clawId, authentication);
        try {
            String result = sandboxClient.getWorkspace(claw.getOwnerUserId(), clawId);
            return jsonResponse(parseJson(result));
        } catch (SandboxClient.SandboxClientException e) {
            log.warn("sandbox workspace failed: claw_id={} error={}", clawId, e.getMessage());
            return jsonError(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @GetMapping("/files")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> listFiles(
            @PathVariable String clawId,
            @RequestParam(defaultValue = ".") String path,
            Authentication authentication) {
        ClawEntity claw = requireOwned(clawId, authentication);
        try {
            String result = sandboxClient.listFiles(claw.getOwnerUserId(), clawId, path);
            return jsonResponse(parseJson(result));
        } catch (SandboxClient.SandboxClientException e) {
            log.warn("sandbox list files failed: claw_id={} path={} error={}", clawId, path, e.getMessage());
            return jsonError(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @GetMapping("/files/{*filePath}")
    @PreAuthorize("hasAuthority('claw:read')")
    public ResponseEntity<?> getFile(
            @PathVariable String clawId,
            @PathVariable String filePath,
            Authentication authentication) {
        ClawEntity claw = requireOwned(clawId, authentication);
        try {
            String result = sandboxClient.getFile(claw.getOwnerUserId(), clawId, filePath);
            JsonNode json = parseJson(result);
            if (json.isTextual() && json.asText().length() > MAX_FILE_BYTES) {
                return jsonError(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds max size: " + MAX_FILE_BYTES + " bytes");
            }
            return jsonResponse(json);
        } catch (SandboxClient.SandboxClientException e) {
            log.warn("sandbox get file failed: claw_id={} file={} error={}", clawId, filePath, e.getMessage());
            return jsonError(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    @PutMapping("/files/{*filePath}")
    @PreAuthorize("hasAuthority('claw:update')")
    public ResponseEntity<?> putFile(
            @PathVariable String clawId,
            @PathVariable String filePath,
            @RequestBody SandboxWriteFileRequest request,
            Authentication authentication) {
        ClawEntity claw = requireOwned(clawId, authentication);
        String content = request == null || request.content() == null ? "" : request.content();
        if (content.length() > MAX_FILE_BYTES) {
            return jsonError(HttpStatus.PAYLOAD_TOO_LARGE, "file content exceeds max size: " + MAX_FILE_BYTES + " bytes");
        }
        try {
            String result = sandboxClient.putFile(claw.getOwnerUserId(), clawId, filePath, content);
            return jsonResponse(parseJson(result));
        } catch (SandboxClient.SandboxClientException e) {
            log.warn("sandbox put file failed: claw_id={} file={} error={}", clawId, filePath, e.getMessage());
            return jsonError(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    // ---- Owner validation ----

    private ClawEntity requireOwned(String clawId, Authentication authentication) {
        ClawEntity claw = claws.findById(clawId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claw not found"));
        if (!isAdmin(authentication) && !Objects.equals(claw.getOwnerUserId(), actorId(authentication))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Claw not found");
        }
        return claw;
    }

    // ---- JSON helpers ----

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            // If the runner returns non-JSON, wrap as a text node
            return objectMapper.valueToTree(Map.of("result", raw != null ? raw : ""));
        }
    }

    private ResponseEntity<JsonNode> jsonResponse(JsonNode body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private ResponseEntity<Map<String, String>> jsonError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", message != null ? message : "sandbox proxy error"));
    }

    // ---- Auth helpers ----

    private boolean isAdmin(Authentication authentication) {
        Set<String> authorities = authentication == null ? Set.of() : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        return authorities.contains("user:manage");
    }

    private String actorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return null;
    }
}
