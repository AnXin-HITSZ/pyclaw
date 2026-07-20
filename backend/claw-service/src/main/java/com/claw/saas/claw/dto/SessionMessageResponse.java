package com.claw.saas.claw.dto;

public record SessionMessageResponse(
        String role,
        String content,
        long timestamp
) {}
