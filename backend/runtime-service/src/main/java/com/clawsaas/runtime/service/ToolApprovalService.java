package com.clawsaas.runtime.service;

import com.clawsaas.runtime.domain.AuthenticatedPrincipal;
import com.clawsaas.runtime.domain.ToolApprovalDecision;
import com.clawsaas.runtime.dto.PyclawApprovalResponse;
import com.clawsaas.runtime.dto.ToolApprovalResponse;
import com.clawsaas.runtime.entity.ToolApprovalRequestEntity;
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
