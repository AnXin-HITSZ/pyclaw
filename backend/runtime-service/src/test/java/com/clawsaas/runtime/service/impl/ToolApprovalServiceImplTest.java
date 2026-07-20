package com.clawsaas.runtime.service.impl;

import com.clawsaas.runtime.client.AuditLogClient;
import com.clawsaas.runtime.client.ClawServiceClient;
import com.clawsaas.runtime.domain.ToolApprovalStatus;
import com.clawsaas.runtime.entity.ToolApprovalRequestEntity;
import com.clawsaas.runtime.exception.ApiException;
import com.clawsaas.runtime.repository.ToolApprovalRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolApprovalServiceImplTest {

    private ToolApprovalRequestRepository repository;
    private ClawServiceClient clawServiceClient;
    private ObjectMapper objectMapper;
    private AuditLogClient auditLogClient;
    private ToolApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ToolApprovalRequestRepository.class);
        clawServiceClient = new ClawServiceClient();
        objectMapper = new ObjectMapper();
        auditLogClient = new AuditLogClient();
        service = new ToolApprovalServiceImpl(repository, clawServiceClient, objectMapper, auditLogClient);
    }

    @Test
    void toResponseShouldMapAllFields() {
        ToolApprovalRequestEntity entity = new ToolApprovalRequestEntity();
        entity.setId("approval-1");
        entity.setClawId("claw-1");
        entity.setClawName("test-claw");
        entity.setSessionId("session-1");
        entity.setAgentId("agent-1");
        entity.setAgentKey("key-1");
        entity.setToolName("bash");
        entity.setRisk("high");
        entity.setStatus(ToolApprovalStatus.PENDING);
        entity.setArgumentsPreview("{}");
        entity.setPendingStateKey("pk-1");
        entity.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        entity.setCreatedAt(OffsetDateTime.now());

        var response = service.toResponse(entity);

        assertEquals("approval-1", response.id());
        assertEquals("PENDING", response.status());
        assertEquals("bash", response.toolName());
        assertEquals("high", response.risk());
        assertEquals("claw-1", response.clawId());
        assertEquals("test-claw", response.clawName());
    }

    @Test
    void requireOwnedPendingShouldThrowWhenNotFound() {
        when(repository.findByIdAndClawIdAndOwnerUserId("approval-1", "claw-1", "user-1"))
                .thenReturn(Optional.empty());

        var principal = new com.clawsaas.runtime.domain.AuthenticatedPrincipal(
                "user-1", "user-1", "USER", List.of());

        assertThrows(ApiException.class, () ->
                service.requireOwnedPending("claw-1", "approval-1", principal));
    }

    @Test
    void markConsumedShouldUpdateStatus() {
        ToolApprovalRequestEntity entity = new ToolApprovalRequestEntity();
        entity.setId("approval-1");
        entity.setStatus(ToolApprovalStatus.PENDING);
        entity.setUpdatedAt(OffsetDateTime.now());

        when(repository.save(any())).thenReturn(entity);

        ToolApprovalRequestEntity result = service.markConsumed(entity, null);

        assertEquals(ToolApprovalStatus.CONSUMED, result.getStatus());
        verify(repository).save(any());
    }
}
