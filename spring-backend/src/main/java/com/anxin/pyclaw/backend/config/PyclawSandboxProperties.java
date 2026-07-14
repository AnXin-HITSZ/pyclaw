package com.anxin.pyclaw.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pyclaw.sandbox")
public class PyclawSandboxProperties {
    private boolean enabled;
    private String namespacePrefix = "pyclaw-user";
    private String namespaceLabelKey = "pyclaw.io/owner-user-id";
    private String runnerImage;
    private String runnerImagePullPolicy = "IfNotPresent";
    private int runnerPort = 8000;
    private String workspaceMountPath = "/workspace";
    private String pvcStorageSize = "1Gi";
    private String pvcStorageClassName;
    private String cpuRequest = "100m";
    private String memoryRequest = "256Mi";
    private String cpuLimit = "500m";
    private String memoryLimit = "768Mi";
    private String serviceAccountName = "default";
    private String imagePullSecretName;
    private String imagePullSecretSourceNamespace;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getNamespacePrefix() { return namespacePrefix; }
    public void setNamespacePrefix(String namespacePrefix) { this.namespacePrefix = namespacePrefix; }
    public String getNamespaceLabelKey() { return namespaceLabelKey; }
    public void setNamespaceLabelKey(String namespaceLabelKey) { this.namespaceLabelKey = namespaceLabelKey; }
    public String getRunnerImage() { return runnerImage; }
    public void setRunnerImage(String runnerImage) { this.runnerImage = runnerImage; }
    public String getRunnerImagePullPolicy() { return runnerImagePullPolicy; }
    public void setRunnerImagePullPolicy(String runnerImagePullPolicy) { this.runnerImagePullPolicy = runnerImagePullPolicy; }
    public int getRunnerPort() { return runnerPort; }
    public void setRunnerPort(int runnerPort) { this.runnerPort = runnerPort; }
    public String getWorkspaceMountPath() { return workspaceMountPath; }
    public void setWorkspaceMountPath(String workspaceMountPath) { this.workspaceMountPath = workspaceMountPath; }
    public String getPvcStorageSize() { return pvcStorageSize; }
    public void setPvcStorageSize(String pvcStorageSize) { this.pvcStorageSize = pvcStorageSize; }
    public String getPvcStorageClassName() { return pvcStorageClassName; }
    public void setPvcStorageClassName(String pvcStorageClassName) { this.pvcStorageClassName = pvcStorageClassName; }
    public String getCpuRequest() { return cpuRequest; }
    public void setCpuRequest(String cpuRequest) { this.cpuRequest = cpuRequest; }
    public String getMemoryRequest() { return memoryRequest; }
    public void setMemoryRequest(String memoryRequest) { this.memoryRequest = memoryRequest; }
    public String getCpuLimit() { return cpuLimit; }
    public void setCpuLimit(String cpuLimit) { this.cpuLimit = cpuLimit; }
    public String getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(String memoryLimit) { this.memoryLimit = memoryLimit; }
    public String getServiceAccountName() { return serviceAccountName; }
    public void setServiceAccountName(String serviceAccountName) { this.serviceAccountName = serviceAccountName; }
    public String getImagePullSecretName() { return imagePullSecretName; }
    public void setImagePullSecretName(String imagePullSecretName) { this.imagePullSecretName = imagePullSecretName; }
    public String getImagePullSecretSourceNamespace() { return imagePullSecretSourceNamespace; }
    public void setImagePullSecretSourceNamespace(String imagePullSecretSourceNamespace) { this.imagePullSecretSourceNamespace = imagePullSecretSourceNamespace; }
}
