package com.claw.saas.claw.service;

import com.claw.saas.claw.domain.AgentConfigEntity;
import com.claw.saas.claw.domain.AgentToolPolicyEntity;
import com.claw.saas.claw.dto.AgentConfigRequest;
import com.claw.saas.claw.dto.AgentConfigResponse;
import com.claw.saas.claw.dto.AgentToolPolicyResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface AgentConfigService {

    List<AgentConfigResponse> list(Authentication authentication);

    AgentConfigResponse get(String id, Authentication authentication);

    AgentConfigResponse create(AgentConfigRequest request, Authentication authentication);

    AgentConfigResponse update(String id, AgentConfigRequest request, Authentication authentication);

    void delete(String id, Authentication authentication);

    AgentConfigEntity requireEnabledByKey(String agentKey);

    AgentToolPolicyEntity requirePolicy(String agentId);

    AgentConfigResponse toResponse(AgentConfigEntity entity);

    AgentToolPolicyResponse toPolicyResponse(AgentToolPolicyEntity entity);

    List<String> readList(String json);

    List<String> readListOrNull(String json);
}
