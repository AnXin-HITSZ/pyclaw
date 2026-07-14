package com.anxin.pyclaw.backend.session;

import java.util.List;

public record SessionDetailResponse(
        SessionSummaryResponse meta,
        List<SessionMessageResponse> messages
) {}
