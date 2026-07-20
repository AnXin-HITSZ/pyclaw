package com.claw.saas.gateway.service;

import com.claw.saas.gateway.dto.LoginRequest;
import com.claw.saas.gateway.dto.LoginResponse;
import com.claw.saas.gateway.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);
}
