package com.claw.saas.runtime.dto;

import java.util.List;

public record SaasClawToolCatalogResponse(
        List<String> profiles,
        List<SaasClawToolCatalogEntry> tools
) {}
