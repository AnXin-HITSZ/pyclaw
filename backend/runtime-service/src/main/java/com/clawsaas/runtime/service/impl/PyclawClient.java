package com.clawsaas.runtime.service.impl;

import com.clawsaas.runtime.config.RuntimeProperties;
import com.clawsaas.runtime.dto.*;
import com.clawsaas.runtime.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * HTTP client for calling the FastAPI Pyclaw Runtime API.
 */
@Component
public class PyclawClient {
    private final RuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host",
            "proxy-connection", "http2-settings"
    );

    public PyclawClient(RuntimeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(properties.readTimeoutSeconds());
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .build();
    }

    public PyclawAgentRunResponse runAgent(PyclawAgentRunRequest request) {
        return postForAgentRunResponse("/v1/agent/run", request, "Agent run failed");
    }

    public PyclawAgentRunResponse resumeAgent(PyclawAgentResumeRequest request) {
        return postForAgentRunResponse("/v1/agent/resume", request, "Agent resume failed");
    }

    private PyclawAgentRunResponse postForAgentRunResponse(String path, Object requestBody, String failureMessage) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            String baseUrl = trimTrailingSlash(properties.baseUrl());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            addInternalAuthorization(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), PyclawAgentRunResponse.class);
            }
            throw new ApiException(HttpStatus.valueOf(response.statusCode()), failureMessage + ": " + response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, failureMessage + ": " + e.getMessage());
        }
    }

    public PyclawToolCatalogResponse toolCatalog() {
        try {
            String baseUrl = trimTrailingSlash(properties.baseUrl());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/tools/catalog"))
                    .GET();
            addInternalAuthorization(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), PyclawToolCatalogResponse.class);
            }
            throw new ApiException(HttpStatus.valueOf(response.statusCode()), "Tool catalog failed: " + response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Tool catalog failed: " + e.getMessage());
        }
    }

    public PyclawToolResolveResponse resolveTools(PyclawToolResolveRequest request) {
        try {
            String body = objectMapper.writeValueAsString(request);
            String baseUrl = trimTrailingSlash(properties.baseUrl());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/tools/resolve"))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            addInternalAuthorization(builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), PyclawToolResolveResponse.class);
            }
            throw new ApiException(HttpStatus.valueOf(response.statusCode()), "Tool resolve failed: " + response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Tool resolve failed: " + e.getMessage());
        }
    }

    private void addInternalAuthorization(HttpRequest.Builder builder) {
        String token = properties.internalToken();
        if (token != null && !token.isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
