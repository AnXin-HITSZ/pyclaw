package com.claw.saas.runtime.service.impl;

import com.claw.saas.runtime.dto.*;
import com.claw.saas.runtime.service.ToolCatalogService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ToolCatalogServiceImpl implements ToolCatalogService {

    private final SaasClawClient saasClawClient;

    public ToolCatalogServiceImpl(SaasClawClient saasClawClient) {
        this.saasClawClient = saasClawClient;
    }

    @Override
    public List<ToolCatalogEntryResponse> catalog() {
        SaasClawToolCatalogResponse response = saasClawClient.toolCatalog();
        return response.tools().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<String> profiles() {
        SaasClawToolCatalogResponse response = saasClawClient.toolCatalog();
        if (response.profiles() == null || response.profiles().isEmpty()) {
            return List.of("minimal", "readonly", "messaging", "coding", "full");
        }
        return response.profiles();
    }

    @Override
    public EffectiveToolsResponse effective(EffectiveToolsRequest request) {
        String profile = normalizeProfile(request.profile());
        SaasClawToolResolveRequest resolveRequest = new SaasClawToolResolveRequest(
                profile,
                request.allow(),
                request.deny(),
                request.alsoAllow(),
                request.readonly()
        );
        SaasClawToolResolveResponse response = saasClawClient.resolveTools(resolveRequest);

        List<String> effectiveTools = response.tools().stream()
                .map(SaasClawToolCatalogEntry::name)
                .sorted()
                .collect(Collectors.toList());

        List<String> deniedTools = response.deniedTools() == null ? List.of() :
                response.deniedTools().stream()
                        .map(SaasClawDeniedTool::name)
                        .sorted()
                        .collect(Collectors.toList());

        return new EffectiveToolsResponse(response.profile(), effectiveTools, deniedTools);
    }

    private ToolCatalogEntryResponse toResponse(SaasClawToolCatalogEntry entry) {
        return new ToolCatalogEntryResponse(
                entry.name(),
                entry.label(),
                entry.description(),
                entry.sectionId(),
                entry.sectionId(),
                entry.executionScope(),
                entry.profiles(),
                entry.tags(),
                entry.risk(),
                entry.readonly(),
                entry.promptHint()
        );
    }

    private String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return "coding";
        }
        return profile.trim().toLowerCase().replace('_', '-').replace('-', '_');
    }
}
