package com.anxin.pyclaw.backend;

import com.anxin.pyclaw.backend.config.PyclawRuntimeProperties;
import com.anxin.pyclaw.backend.config.PyclawSandboxProperties;
import com.anxin.pyclaw.backend.config.PyclawSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableJpaAuditing
@EnableMethodSecurity
@EnableConfigurationProperties({PyclawSecurityProperties.class, PyclawRuntimeProperties.class, PyclawSandboxProperties.class})
public class PyclawBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PyclawBackendApplication.class, args);
    }
}
