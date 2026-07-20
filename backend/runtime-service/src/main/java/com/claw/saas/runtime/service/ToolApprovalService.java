package com.claw.saas.runtime.service;

import com.claw.saas.runtime.domain.AuthenticatedPrincipal;
import com.claw.saas.runtime.domain.ToolApprovalDecision;
import com.claw.saas.runtime.dto.PyclawApprovalResponse;
import com.claw.saas.runtime.dto.ToolApprovalResponse;
import com.claw.saas.runtime.entity.ToolApprovalRequestEntity;
import org.springframework.security.core.Authentication;

public interface ToolApprovalService {

    ToolApprovalResponse createFromPyclaw(
            String clawId, String ownerUserId, String clawName,
            String sessionId, String agentId, String agentKey, String roleKey,
            PyclawApprovalResponse payload, Authentication authentication);

    ToolApprovalResponse createFromPyclaw(
            String clawId, String ownerUserId, String clawName,
            String sessionId, String agentId, String agentKey, String roleKey,
            PyclawApprovalResponse payload,
            String executingAgentInstanceId, String executingRoleKey,
            String callingAgentInstanceId, String callingRoleKey,
            String conversationId, Authentication authentication);

    ToolApprovalRequestEntity requireOwnedPending(String clawId, String approvalId, AuthenticatedPrincipal principal);

    ToolApprovalRequestEntity requireOwnedActionable(String clawId, String approvalId,
                                                      AuthenticatedPrincipal principal, ToolApprovalDecision decision);

    ToolApprovalRequestEntity markApprovedForResume(ToolApprovalRequestEntity entity, Authentication authentication,
                                                     AuthenticatedPrincipal principal);

    ToolApprovalRequestEntity markRejectedForResume(ToolApprovalRequestEntity entity, String reason,
                                                     Authentication authentication, AuthenticatedPrincipal principal);

    ToolApprovalRequestEntity markConsumed(ToolApprovalRequestEntity entity, Authentication authentication);

    ToolApprovalRequestEntity markResumeFailed(ToolApprovalRequestEntity entity, Authentication authentication, String error);

    ToolApprovalResponse toResponse(ToolApprovalRequestEntity entity);
}
