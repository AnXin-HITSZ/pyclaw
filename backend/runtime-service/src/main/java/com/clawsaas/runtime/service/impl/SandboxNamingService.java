package com.clawsaas.runtime.service.impl;

import com.clawsaas.runtime.config.SandboxProperties;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * DNS naming utilities for sandbox runner services.
 */
@Component
public class SandboxNamingService {

    private final SandboxProperties properties;

    public SandboxNamingService(SandboxProperties properties) {
        this.properties = properties;
    }

    public String namespaceForUser(String userId) {
        return dnsName(properties.getNamespacePrefix() + "-" + userId);
    }

    public String runnerName(String clawId) {
        return dnsName("sandbox-runner-" + clawId);
    }

    public String workspacePvcName(String clawId) {
        return dnsName("workspace-" + clawId);
    }

    public String serviceBaseUrl(String userId, String clawId) {
        String namespace = namespaceForUser(userId);
        String svcName = runnerName(clawId);
        return String.format("http://%s.%s.svc.cluster.local:%d", svcName, namespace, properties.getRunnerPort());
    }

    public static String clawSecretName(String clawId) {
        return "claw-secret-" + clawId;
    }

    public static String userSecretName(String userId) {
        return "user-secret-" + userId;
    }

    public static String dnsLabel(String value) {
        return dnsName(value == null ? "unknown" : value);
    }

    public static String dnsName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            normalized = "x";
        }
        if (normalized.length() > 63) {
            normalized = normalized.substring(0, 63).replaceAll("-$", "");
        }
        return normalized;
    }
}
