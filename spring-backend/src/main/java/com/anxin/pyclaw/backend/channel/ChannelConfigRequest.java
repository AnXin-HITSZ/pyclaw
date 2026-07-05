package com.anxin.pyclaw.backend.channel;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ChannelConfigRequest(
        @NotBlank String channelType,
        @NotBlank String name,
        Map<String, Object> config,
        String secretRef,
        boolean enabled
) {
}
