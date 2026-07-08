package com.anxin.pyclaw.backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anxin.pyclaw.backend.channel.ChannelConfigEntity;
import com.anxin.pyclaw.backend.channel.ChannelConfigRepository;
import com.anxin.pyclaw.backend.pyclaw.PyclawClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SecuritySmokeTest {
    @TempDir
    static java.nio.file.Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChannelConfigRepository channelConfigs;

    @MockBean
    private PyclawClient pyclawClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            try {
                return "jdbc:h2:file:" + Files.createTempDirectory(tempDir, "db").resolve("pyclaw-test")
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            } catch (Exception exc) {
                throw new IllegalStateException(exc);
            }
        });
        registry.add("pyclaw.security.jwt-signing-secret", () -> "test-secret-that-is-long-enough-for-hs256");
        registry.add("pyclaw.security.bootstrap-admin-password", () -> "ChangeMe123!");
        registry.add("pyclaw.runtime.internal-token", () -> "internal-token");
    }

    @Test
    void healthzIsPublic() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void channelWebhookIsPublicAndForwarded() throws Exception {
        when(pyclawClient.forwardChannelWebhook(eq("wechat"), any(), any(byte[].class), eq("GET"), anyMap()))
                .thenReturn(ResponseEntity.ok("pong".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/webhooks/wechat").queryParam("echostr", "pong"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void internalChannelRuntimeConfigRequiresToken() throws Exception {
        mockMvc.perform(get("/api/internal/channels/wechat/runtime-config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing internal API token"));
    }

    @Test
    void internalChannelRuntimeConfigReturnsEnabledDatabaseConfig() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        ChannelConfigEntity entity = new ChannelConfigEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setChannelType("wechat");
        entity.setName("demo-wechat");
        entity.setConfigJson("{\"accountId\":\"gh_demo\",\"token\":\"wechat-token\",\"appId\":\"wx_app\"}");
        entity.setEnabled(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        channelConfigs.save(entity);

        mockMvc.perform(get("/api/internal/channels/wechat/runtime-config")
                        .queryParam("accountId", "gh_demo")
                        .header("Authorization", "Bearer internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("wechat"))
                .andExpect(jsonPath("$.accountId").value("gh_demo"))
                .andExpect(jsonPath("$.name").value("demo-wechat"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.config.token").value("wechat-token"))
                .andExpect(jsonPath("$.config.appId").value("wx_app"));
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapAdminCanLoginAndReadMe() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = json.get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }
}
