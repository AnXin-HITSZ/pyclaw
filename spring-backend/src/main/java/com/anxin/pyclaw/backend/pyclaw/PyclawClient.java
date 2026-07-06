package com.anxin.pyclaw.backend.pyclaw;

import com.anxin.pyclaw.backend.common.ApiException;
import com.anxin.pyclaw.backend.config.PyclawRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PyclawClient {
    private final PyclawRuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PyclawClient(PyclawRuntimeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .build();
    }

    public PyclawAgentRunResponse runAgent(PyclawAgentRunRequest request) {
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(properties.baseUrl()) + "/v1/agent/run"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (properties.apiToken() != null && !properties.apiToken().isBlank()) {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "pyclaw call failed: " + response.body());
            }
            return objectMapper.readValue(response.body(), PyclawAgentRunResponse.class);
        } catch (ApiException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "pyclaw call failed: " + exc.getMessage());
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
