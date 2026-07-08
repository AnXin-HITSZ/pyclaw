package com.anxin.pyclaw.backend.channel;

import com.anxin.pyclaw.backend.common.ApiException;
import com.anxin.pyclaw.backend.config.PyclawRuntimeProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/channels")
public class ChannelRuntimeConfigController {
    private final ChannelConfigRepository repository;
    private final ObjectMapper objectMapper;
    private final PyclawRuntimeProperties properties;

    public ChannelRuntimeConfigController(
            ChannelConfigRepository repository,
            ObjectMapper objectMapper,
            PyclawRuntimeProperties properties
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @GetMapping("/{channelType}/runtime-config")
    public ChannelRuntimeConfigResponse runtimeConfig(
            @PathVariable String channelType,
            @RequestParam(required = false) String accountId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        requireInternalToken(authorization);
        String normalizedChannel = normalize(channelType);
        List<ChannelConfigEntity> candidates = repository.findByChannelTypeIgnoreCaseAndEnabledTrueOrderByUpdatedAtDesc(normalizedChannel);
        ChannelRuntimeConfigResponse selected = selectConfig(normalizedChannel, accountId, candidates, true);
        if (selected == null) {
            selected = selectConfig(normalizedChannel, accountId, candidates, false);
        }
        if (selected != null) {
            return selected;
        }
        return new ChannelRuntimeConfigResponse(normalizedChannel, blankToNull(accountId), null, false, Map.of());
    }

    private void requireInternalToken(String authorization) {
        String expected = properties.internalToken();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Internal channel config token is not configured");
        }
        String prefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(prefix)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing internal API token");
        }
        String actual = authorization.substring(prefix.length());
        if (!expected.equals(actual)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid internal API token");
        }
    }

    private Map<String, Object> parseConfig(ChannelConfigEntity entity) {
        try {
            return objectMapper.readValue(entity.getConfigJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception exc) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid channel config JSON");
        }
    }

    private ChannelRuntimeConfigResponse selectConfig(
            String channel,
            String accountId,
            List<ChannelConfigEntity> candidates,
            boolean exactOnly
    ) {
        for (ChannelConfigEntity entity : candidates) {
            Map<String, Object> config = parseConfig(entity);
            String configuredAccountId = firstNonBlank(config.get("accountId"), config.get("account_id"));
            if (!accountMatches(configuredAccountId, accountId, exactOnly)) {
                continue;
            }
            String resolvedAccountId = firstNonBlank(configuredAccountId, accountId);
            return new ChannelRuntimeConfigResponse(channel, resolvedAccountId, entity.getName(), true, config);
        }
        return null;
    }

    private boolean accountMatches(String configuredAccountId, String accountId, boolean exactOnly) {
        String requested = blankToNull(accountId);
        if (requested == null) {
            return true;
        }
        if (exactOnly) {
            return requested.equals(configuredAccountId);
        }
        return configuredAccountId == null;
    }

    private String normalize(String channelType) {
        if (channelType == null || channelType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "channelType is required");
        }
        return channelType.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = blankToNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String blankToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
