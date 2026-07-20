package com.clawsaas.runtime.service;

import java.util.Map;

public interface SandboxOrchestratorService {
    boolean isEnabled();
    String namespaceForUser(String userId);
    void ensureUserNamespace(String userId, String username);
    void ensureClawSandbox(String userId, String username, String clawId, String clawName);
    void ensureClawSecret(String userId, String clawId, Map<String, String> values);
    void ensureUserSecret(String userId, Map<String, String> values);
    void deleteClawSecret(String userId, String clawId);
    void deleteClawSecretByName(String userId, String secretName);
    void scaleClawDeployment(String userId, String clawId, int replicas);
    void scaleUserDeployments(String userId, int replicas);
    void deleteClawSandbox(String userId, String clawId);
}
