package com.claw.saas.runtime.dto;

import java.util.List;

public record EffectiveToolsResponse(
        String profile,
        List<String> effectiveTools,
        List<String> deniedTools
) {}
