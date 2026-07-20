package com.claw.saas.runtime.service;

import com.claw.saas.runtime.dto.EffectiveToolsRequest;
import com.claw.saas.runtime.dto.EffectiveToolsResponse;
import com.claw.saas.runtime.dto.ToolCatalogEntryResponse;
import java.util.List;

public interface ToolCatalogService {
    List<ToolCatalogEntryResponse> catalog();
    List<String> profiles();
    EffectiveToolsResponse effective(EffectiveToolsRequest request);
}
