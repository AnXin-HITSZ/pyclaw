package com.claw.saas.runtime.dto;

import java.util.List;

public record ToolCatalogEntryResponse(
        String name,
        String label,
        String description,
        String sectionId,
        String category,
        String executionScope,
        List<String> profiles,
        List<String> tags,
        String risk,
        boolean readonly,
        String promptHint
) {}
