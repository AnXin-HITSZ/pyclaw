package com.claw.saas.claw.service;

import com.claw.saas.claw.domain.ClawAgentEntity;
import com.claw.saas.claw.dto.ClawRequest;
import com.claw.saas.claw.dto.ClawResponse;
import com.claw.saas.claw.dto.ClawRoleResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface ClawService {

    List<ClawResponse> list(Authentication authentication);

    ClawResponse get(String id, Authentication authentication);

    ClawResponse create(ClawRequest request, Authentication authentication);

    ClawResponse update(String id, ClawRequest request, Authentication authentication);

    ClawResponse syncRoutes(String id, Authentication authentication);

    void delete(String id, Authentication authentication);

    ClawResponse toResponse(com.claw.saas.claw.domain.ClawEntity entity);

    ClawRoleResponse toRoleResponsePublic(ClawAgentEntity entity);
}
