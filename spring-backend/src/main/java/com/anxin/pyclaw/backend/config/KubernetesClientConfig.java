package com.anxin.pyclaw.backend.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesClientConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "pyclaw.sandbox", name = "enabled", havingValue = "true")
    public KubernetesClient kubernetesClient() {
        Config config = new ConfigBuilder().build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }
}