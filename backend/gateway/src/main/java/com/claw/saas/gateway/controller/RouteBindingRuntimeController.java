package com.claw.saas.gateway.controller;

import com.claw.saas.gateway.dto.RouteBindingResponse;
import com.claw.saas.gateway.service.RouteBindingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for querying active route bindings.
 *
 * Authentication is handled upstream by
 * {@link com.claw.saas.gateway.config.InternalServiceAuthFilter} —
 * do NOT add a redundant token check here.
 */
@RestController
@RequestMapping("/api/internal/route-bindings")
public class RouteBindingRuntimeController {
    private final RouteBindingService service;

    public RouteBindingRuntimeController(RouteBindingService service) {
        this.service = service;
    }

    @GetMapping("/runtime")
    public List<RouteBindingResponse> runtime() {
        return service.runtimeList();
    }
}
