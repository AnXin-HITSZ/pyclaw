package com.anxin.pyclaw.backend.session;

import java.util.List;

public record SessionMessageResponse(
        String role,
        String content,
        long timestamp
) {}
