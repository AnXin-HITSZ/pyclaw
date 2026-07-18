package com.anxin.pyclaw.backend.orchestrator;

import com.anxin.pyclaw.backend.claw.ClawAgentEntity;
import com.anxin.pyclaw.backend.claw.ClawAgentRepository;
import com.anxin.pyclaw.backend.claw.ClawEntity;
import com.anxin.pyclaw.backend.claw.ClawRepository;
import com.anxin.pyclaw.backend.clawchat.ClawChatRunRequest;
import com.anxin.pyclaw.backend.common.ApiException;
import com.anxin.pyclaw.backend.conversation.AgentMemorySessionResolver;
import com.anxin.pyclaw.backend.conversation.ConversationEntity;
import com.anxin.pyclaw.backend.conversation.ConversationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ConversationOrchestratorService {

    private final ClawRepository claws;
    private final ClawAgentRepository clawAgents;
    private final ConversationService conversationService;
    private final AgentMemorySessionResolver memorySessionResolver;

    public ConversationOrchestratorService(
            ClawRepository claws,
            ClawAgentRepository clawAgents,
            ConversationService conversationService,
            AgentMemorySessionResolver memorySessionResolver
    ) {
        this.claws = claws;
        this.clawAgents = clawAgents;
        this.conversationService = conversationService;
        this.memorySessionResolver = memorySessionResolver;
    }

    /**
     * Resolve the turn's agent, conversation, and runtime session.
     *
     * Priority (from ARCHITECTURE.md):
     * 1. agentInstanceId explicitly specified
     * 2. roleKey explicitly specified
     * 3. Current conversation's active agent (future: conversation-level pinned agent)
     * 4. defaultRole = true
     * 5. Auto-routing (future: capability-based)
     */
    public OrchestratorResult resolveTurnAgent(String clawId, ClawChatRunRequest request, Authentication authentication) {
        ClawEntity claw = requireOwnedClaw(clawId, authentication);

        // 1. Conversation: use existing or create
        ConversationEntity conversation = conversationService.getOrCreate(
                request.conversationId(), clawId, authentication);

        // 2. Resolve agent instance
        ClawAgentEntity instance = resolveAgentInstance(claw, request);

        // 3. Runtime memory session
        String runtimeSessionId = memorySessionResolver.resolve(conversation.getId(), instance.getId());

        return new OrchestratorResult(
                conversation.getId(),
                instance.getId(),
                instance.getAgentId(),
                null,  // agentKey — caller resolves from AgentConfigService
                instance.getRoleKey(),
                instance.getDisplayName(),
                instance.getLocalProfile(), // caller falls back to package default
                runtimeSessionId
        );
    }

    private ClawAgentEntity resolveAgentInstance(ClawEntity claw, ClawChatRunRequest request) {
        List<ClawAgentEntity> roles = clawAgents.findByClawIdOrderBySortOrderAscCreatedAtAsc(claw.getId());
        List<ClawAgentEntity> enabled = roles.stream().filter(ClawAgentEntity::isEnabled).toList();

        if (enabled.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "No enabled agent instance configured for this Claw");
        }

        // Priority 1: explicit agentInstanceId
        if (request.agentInstanceId() != null && !request.agentInstanceId().isBlank()) {
            return enabled.stream()
                    .filter(r -> request.agentInstanceId().equals(r.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Agent instance '" + request.agentInstanceId() + "' not found or disabled in this Claw"));
        }

        // Priority 2: explicit roleKey
        if (request.roleKey() != null && !request.roleKey().isBlank()) {
            return enabled.stream()
                    .filter(r -> request.roleKey().equals(r.getRoleKey()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Role '" + request.roleKey() + "' not found or disabled in this Claw"));
        }

        // Priority 3+4: defaultRole, then first enabled
        return enabled.stream().filter(ClawAgentEntity::isDefaultRole).findFirst()
                .orElse(enabled.get(0));
    }

    private ClawEntity requireOwnedClaw(String clawId, Authentication authentication) {
        ClawEntity claw = claws.findById(clawId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Claw not found"));
        // Ownership checked by ClawChatService before calling here — light check
        return claw;
    }
}
