package com.clawsaas.runtime.service;

import com.clawsaas.runtime.dto.EffectiveToolsRequest;
import com.clawsaas.runtime.dto.EffectiveToolsResponse;
import com.clawsaas.runtime.dto.ToolCatalogEntryResponse;
import java.util.List;

public interface ToolCatalogService {
    List<ToolCatalogEntryResponse> catalog();
    List<String> profiles();
    EffectiveToolsResponse effective(EffectiveToolsRequest request);
}
