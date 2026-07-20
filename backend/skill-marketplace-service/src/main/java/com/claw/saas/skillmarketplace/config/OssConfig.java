package com.claw.saas.skillmarketplace.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssConfig {

    private static final Logger log = LoggerFactory.getLogger(OssConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "clawsaas.oss")
    public OssProperties ossProperties() {
        return new OssProperties();
    }

    @Bean
    public OSS ossClient(OssProperties properties) {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            log.warn("OSS endpoint not configured — OSS client will not be available");
            return null;
        }
        log.info("Creating OSS client: endpoint={} bucket={}", properties.getEndpoint(), properties.getBucket());
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }

    public static class OssProperties {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket = "claw-saas-artifacts";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }
}
