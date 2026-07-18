package com.anxin.pyclaw.backend.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import com.anxin.pyclaw.backend.claw.ClawEntity;
import com.anxin.pyclaw.backend.claw.ClawRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ConversationServiceTest {

    private final String CLAW_ID = "claw-1";
    private final String OWNER_ID = "user-a";

    private ConversationRepository conversations;
    private ConversationMessageRepository messages;
    private ClawRepository claws;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        messages = mock(ConversationMessageRepository.class);
        claws = mock(ClawRepository.class);
        service = new ConversationService(conversations, messages, claws);

        ClawEntity claw = new ClawEntity();
        claw.setId(CLAW_ID);
        claw.setOwnerUserId(OWNER_ID);
        when(claws.findById(CLAW_ID)).thenReturn(Optional.of(claw));

        when(conversations.save(any(ConversationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(ConversationMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsConversationForClawOwner() {
        ConversationEntity conv = service.create(CLAW_ID, "Test Convo", auth(OWNER_ID, false));
        assertThat(conv.getClawId()).isEqualTo(CLAW_ID);
        assertThat(conv.getOwnerUserId()).isEqualTo(OWNER_ID);
        assertThat(conv.getStatus()).isEqualTo("active");
    }

    @Test
    void savesMessageUpdatesConversationTimestamp() {
        ConversationEntity conv = new ConversationEntity();
        conv.setId("conv-1");
        conv.setOwnerUserId(OWNER_ID);
        conv.setClawId(CLAW_ID);
        conv.setTitle("Test");
        conv.setStatus("active");
        conv.setCreatedAt(OffsetDateTime.now());
        conv.setUpdatedAt(OffsetDateTime.now());
        when(conversations.findById("conv-1")).thenReturn(Optional.of(conv));

        ConversationMessageEntity msg = service.saveMessage(
                "conv-1", OWNER_ID, CLAW_ID, "inst-1", "agent-1",
                "k3s", "k3s", "openai", "gpt-4", "user", "hello");

        assertThat(msg.getConversationId()).isEqualTo("conv-1");
        assertThat(msg.getAgentInstanceId()).isEqualTo("inst-1");
        assertThat(msg.getRoleKey()).isEqualTo("k3s");
    }

    @Test
    void listByUserReturnsUserConversationsOnly() {
        ConversationEntity conv = new ConversationEntity();
        conv.setId("conv-1");
        conv.setOwnerUserId(OWNER_ID);
        conv.setClawId(CLAW_ID);
        conv.setTitle("Test");
        conv.setStatus("active");
        conv.setCreatedAt(OffsetDateTime.now());
        conv.setUpdatedAt(OffsetDateTime.now());
        when(conversations.findByOwnerUserIdOrderByUpdatedAtDesc(OWNER_ID)).thenReturn(List.of(conv));

        List<ConversationEntity> result = service.listByUser(auth(OWNER_ID, false));
        assertThat(result).hasSize(1);
    }

    private Authentication auth(String userId, boolean admin) {
        List<GrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("user:manage"))
                : List.of(new SimpleGrantedAuthority("claw:read"));
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, userId, "USER", authorities);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        doReturn(authorities).when(authentication).getAuthorities();
        return authentication;
    }
}
