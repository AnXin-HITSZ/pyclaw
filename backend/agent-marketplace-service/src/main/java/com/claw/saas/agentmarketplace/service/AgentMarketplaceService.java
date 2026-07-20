package com.claw.saas.agentmarketplace.service;

import com.claw.saas.agentmarketplace.dto.AgentPackageResponse;
import com.claw.saas.agentmarketplace.dto.AgentPackageVersionResponse;
import com.claw.saas.agentmarketplace.dto.AgentPublishRequest;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface AgentMarketplaceService {

    List<AgentPackageResponse> list(Authentication authentication);

    AgentPackageResponse get(String packageId, Authentication authentication);

    AgentPackageVersionResponse publish(String agentId, AgentPublishRequest request, Authentication authentication);

    List<AgentPackageVersionResponse> listVersions(String packageId, Authentication authentication);
}
