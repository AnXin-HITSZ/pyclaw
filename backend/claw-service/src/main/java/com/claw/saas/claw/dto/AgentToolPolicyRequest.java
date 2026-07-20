package com.claw.saas.claw.dto;

import java.util.List;

public record AgentToolPolicyRequest(
        String profile,
        List<String> toolsAllow,
        List<String> toolsDeny,
        List<String> toolsAlsoAllow,
        Boolean readonly
) {
}
