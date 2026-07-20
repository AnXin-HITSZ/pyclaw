package com.claw.saas.gateway.service;

import com.claw.saas.gateway.dto.RouteBindingRequest;
import com.claw.saas.gateway.dto.RouteBindingResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface RouteBindingService {
    List<RouteBindingResponse> list(Authentication authentication);

    List<RouteBindingResponse> runtimeList();

    RouteBindingResponse create(RouteBindingRequest request, Authentication authentication);

    RouteBindingResponse update(String id, RouteBindingRequest request, Authentication authentication);

    void delete(String id, Authentication authentication);
}
