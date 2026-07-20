package com.claw.saas.runtime.dto;

import java.util.List;

public record PyclawToolCatalogResponse(
        List<String> profiles,
        List<PyclawToolCatalogEntry> tools
) {}
