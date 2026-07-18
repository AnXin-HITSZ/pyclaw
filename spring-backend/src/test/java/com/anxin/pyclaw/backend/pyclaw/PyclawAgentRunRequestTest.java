package com.anxin.pyclaw.backend.pyclaw;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PyclawAgentRunRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void serializesConversationIdAndAgentInstanceId() throws Exception {
        PyclawAgentRunRequest req = new PyclawAgentRunRequest(
                "hello", "openai", "session-1", "full",
                "gpt-4", "auto", "https://api.openai.com", "sk-key",
                null, null, null, null,
                "claw-1", "user-1", "My Claw", "k3s", "k3s-agent",
                "http://sandbox.local", "conv-abc", "inst-123"
        );

        String json = mapper.writeValueAsString(req);
        Map<String, Object> map = mapper.readValue(json, Map.class);

        assertThat(map.get("conversation_id")).isEqualTo("conv-abc");
        assertThat(map.get("agent_instance_id")).isEqualTo("inst-123");
    }

    @Test
    void defaultsToNullWhenNotProvided() {
        PyclawAgentRunRequest req = new PyclawAgentRunRequest(
                "hello", "openai", "session-1", "full",
                "gpt-4", "auto", "https://api.openai.com", "sk-key"
        );
        assertThat(req.conversationId()).isNull();
        assertThat(req.agentInstanceId()).isNull();
    }

    @Test
    void resumeRequestCarriesConversationAndInstance() throws Exception {
        PyclawAgentResumeRequest req = new PyclawAgentResumeRequest(
                "approval-1", "APPROVED", null,
                "openai", "gpt-4", "auto", "https://api.openai.com", "sk-key",
                "system", "full",
                List.of(), List.of(), List.of(),
                "http://sandbox.local",
                "conv-xyz", "inst-456"
        );

        String json = mapper.writeValueAsString(req);
        Map<String, Object> map = mapper.readValue(json, Map.class);

        assertThat(map.get("conversation_id")).isEqualTo("conv-xyz");
        assertThat(map.get("agent_instance_id")).isEqualTo("inst-456");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolResolveRequestSerializesFastApiFieldNames() throws Exception {
        PyclawToolResolveRequest req = new PyclawToolResolveRequest(
                "coding",
                null,
                List.of("shell"),
                List.of("discover_agents"),
                false
        );

        String json = mapper.writeValueAsString(req);
        Map<String, Object> map = mapper.readValue(json, Map.class);

        assertThat(map).containsEntry("also_allow", List.of("discover_agents"));
        assertThat(map).doesNotContainKey("alsoAllow");
    }
}
