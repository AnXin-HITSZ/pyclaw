package com.clawsaas.runtime;

import com.clawsaas.runtime.config.RuntimeProperties;
import com.clawsaas.runtime.config.RuntimeSecurityProperties;
import com.clawsaas.runtime.config.SandboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RuntimeProperties.class, SandboxProperties.class, RuntimeSecurityProperties.class})
public class RuntimeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuntimeServiceApplication.class, args);
    }
}
