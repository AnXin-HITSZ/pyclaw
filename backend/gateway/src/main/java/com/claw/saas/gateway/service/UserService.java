package com.claw.saas.gateway.service;

import com.claw.saas.gateway.dto.CreateUserRequest;
import com.claw.saas.gateway.entity.UserEntity;
import java.util.List;

public interface UserService {
    List<UserEntity> list();

    UserEntity create(CreateUserRequest request);

    UserEntity disable(String id);
}
