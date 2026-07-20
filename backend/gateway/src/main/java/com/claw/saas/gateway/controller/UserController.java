package com.claw.saas.gateway.controller;

import com.claw.saas.gateway.dto.CreateUserRequest;
import com.claw.saas.gateway.entity.UserEntity;
import com.claw.saas.gateway.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public List<UserEntity> list() {
        return userService.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public UserEntity create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('user:manage')")
    public UserEntity disable(@PathVariable String id) {
        return userService.disable(id);
    }
}
