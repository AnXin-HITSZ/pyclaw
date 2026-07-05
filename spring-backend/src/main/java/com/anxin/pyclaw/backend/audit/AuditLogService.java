package com.anxin.pyclaw.backend.audit;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actorType, String actorId, String action, String resourceType, String resourceId, boolean success, String errorMessage) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setActorType(actorType);
        entity.setActorId(actorId);
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setSuccess(success);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
