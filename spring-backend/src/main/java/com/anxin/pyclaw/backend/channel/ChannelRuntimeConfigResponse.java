package com.anxin.pyclaw.backend.channel;

import java.util.Map;

public record ChannelRuntimeConfigResponse(
        String channel,
        String accountId,
        String name,
        boolean enabled,
        Map<String, Object> config
) {
}