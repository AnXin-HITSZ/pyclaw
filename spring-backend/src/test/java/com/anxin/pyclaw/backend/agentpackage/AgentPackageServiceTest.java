package com.anxin.pyclaw.backend.agentpackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anxin.pyclaw.backend.agentconfig.AgentConfigEntity;
import com.anxin.pyclaw.backend.agentconfig.AgentConfigRepository;
import com.anxin.pyclaw.backend.agentconfig.AgentToolPolicyEntity;
import com.anxin.pyclaw.backend.agentconfig.AgentToolPolicyRepository;
import com.anxin.pyclaw.backend.audit.AuditLogService;
import com.anxin.pyclaw.backend.auth.AuthenticatedPrincipal;
import com.anxin.pyclaw.backend.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AgentPackageServiceTest {

    private static final String OWNER_ID = "user-a";
    private static final String OTHER_ID = "user-b";

    private AgentPackageRepository packages;
    private AgentPackageVersionRepository versions;
    private AgentConfigRepository agents;
    private AgentToolPolicyRepository policies;
    private AuditLogService auditLogService;
    private AgentPackageService service;

    @BeforeEach
    void setUp() {
        packages = mock(AgentPackageRepository.class);
        versions = mock(AgentPackageVersionRepository.class);
        agents = mock(AgentConfigRepository.class);
        policies = mock(AgentToolPolicyRepository.class);
        auditLogService = mock(AuditLogService.class);
        service = new AgentPackageService(packages, versions, agents, policies, new ObjectMapper(), auditLogService);
    }

    @Test
    void nonOwnerCannotPublish() {
        AgentConfigEntity agent = baseAgent(OWNER_ID);
        when(agents.findById("agent-1")).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.publish("agent-1", publishRequest(), auth(OTHER_ID, false)))
                .isInstanceOfSatisfying(ApiException.class, exc -> assertThat(exc.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(packages, never()).save(any());
    }

    @Test
    void duplicateVersionIsRejected() {
        AgentConfigEntity agent = baseAgent(OWNER_ID);
        when(agents.findById("agent-1")).thenReturn(Optional.of(agent));
        when(policies.findByAgentId("agent-1")).thenReturn(Optional.of(policy("messaging")));

        AgentPackageEntity pkg = existingPackage("pkg-1", OWNER_ID, "k3s-troubleshooter");
        when(packages.findByOwnerUserIdAndPackageKey(OWNER_ID, "k3s-troubleshooter")).thenReturn(Optional.of(pkg));
        when(versions.findByPackageIdAndVersion("pkg-1", "1.0.0")).thenReturn(Optional.of(existingVersion("pkg-1", "1.0.0")));

        assertThatThrownBy(() -> service.publish("agent-1", publishRequest(), auth(OWNER_ID, false)))
                .isInstanceOfSatisfying(ApiException.class, exc -> assertThat(exc.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(versions, never()).save(any());
    }

    @Test
    void publishCreatesNewVersionAndUpdatesLatest() {
        AgentConfigEntity agent = baseAgent(OWNER_ID);
        when(agents.findById("agent-1")).thenReturn(Optional.of(agent));
        when(policies.findByAgentId("agent-1")).thenReturn(Optional.of(policy("messaging")));

        AgentPackageEntity pkg = existingPackage("pkg-1", OWNER_ID, "k3s-troubleshooter");
        when(packages.findByOwnerUserIdAndPackageKey(OWNER_ID, "k3s-troubleshooter")).thenReturn(Optional.of(pkg));
        when(versions.findByPackageIdAndVersion("pkg-1", "1.0.0")).thenReturn(Optional.empty());
        when(packages.save(any(AgentPackageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versions.save(any(AgentPackageVersionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentPackageVersionResponse response = service.publish("agent-1", publishRequest(), auth(OWNER_ID, false));

        assertThat(response.status()).isEqualTo("published");
        assertThat(response.version()).isEqualTo("1.0.0");
        assertThat(response.defaultProfile()).isEqualTo("messaging");
        assertThat(pkg.getLatestVersionId()).isEqualTo(response.id());
        assertThat(pkg.getVisibility()).isEqualTo("public");
        verify(auditLogService).record(eq("USER"), eq(OWNER_ID), eq("agent_package.publish"), eq("agent_package"), eq(response.id()), eq(true), any());
    }

    @Test
    void publicPackageIsListedForEveryone() {
        AgentPackageEntity pub = existingPackage("pkg-1", OWNER_ID, "k3s-troubleshooter");
        pub.setVisibility("public");
        when(packages.findByVisibilityOrderByUpdatedAtDesc("public")).thenReturn(List.of(pub));

        List<AgentPackageResponse> rows = service.list(auth(OTHER_ID, false));
        assertThat(rows).extracting(AgentPackageResponse::id).contains("pkg-1");
    }

    @Test
    void privatePackageOnlyVisibleToOwner() {
        AgentPackageEntity priv = existingPackage("pkg-1", OWNER_ID, "k3s-troubleshooter");
        priv.setVisibility("private");
        when(packages.findById("pkg-1")).thenReturn(Optional.of(priv));

        assertThatThrownBy(() -> service.get("pkg-1", auth(OTHER_ID, false)))
                .isInstanceOfSatisfying(ApiException.class, exc -> assertThat(exc.status()).isEqualTo(HttpStatus.NOT_FOUND));

        when(packages.findByOwnerUserIdOrderByUpdatedAtDesc(OWNER_ID)).thenReturn(List.of(priv));
        assertThat(service.get("pkg-1", auth(OWNER_ID, false)).id()).isEqualTo("pkg-1");
    }

    private AgentPublishRequest publishRequest() {
        return new AgentPublishRequest("k3s-troubleshooter", "1.0.0", "public", "排查 K3s 问题", "首次发布");
    }

    private AgentConfigEntity baseAgent(String ownerId) {
        AgentConfigEntity agent = new AgentConfigEntity();
        agent.setId("agent-1");
        agent.setAgentKey("k3s-troubleshooter");
        agent.setName("K3s 排障助手");
        agent.setDescription("排查 K3s、Helm、Pod 问题");
        agent.setSystemPrompt("You are a K3s troubleshooter.");
        agent.setCreatedBy(ownerId);
        return agent;
    }

    private AgentToolPolicyEntity policy(String profile) {
        AgentToolPolicyEntity p = new AgentToolPolicyEntity();
        p.setId("policy-1");
        p.setAgentId("agent-1");
        p.setProfile(profile);
        p.setReadonly("readonly".equals(profile));
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedAt(OffsetDateTime.now());
        return p;
    }

    private AgentPackageEntity existingPackage(String id, String ownerId, String packageKey) {
        AgentPackageEntity pkg = new AgentPackageEntity();
        pkg.setId(id);
        pkg.setOwnerUserId(ownerId);
        pkg.setPackageKey(packageKey);
        pkg.setName("K3s 排障助手");
        pkg.setVisibility("private");
        pkg.setInstallCount(0L);
        pkg.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        pkg.setUpdatedAt(OffsetDateTime.now().minusMinutes(5));
        return pkg;
    }

    private AgentPackageVersionEntity existingVersion(String packageId, String version) {
        AgentPackageVersionEntity ver = new AgentPackageVersionEntity();
        ver.setId("ver-old");
        ver.setPackageId(packageId);
        ver.setVersion(version);
        ver.setStatus("published");
        ver.setDefaultProfile("messaging");
        ver.setCreatedAt(OffsetDateTime.now().minusDays(1));
        return ver;
    }

    private Authentication auth(String userId, boolean admin) {
        Collection<GrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("user:manage"), new SimpleGrantedAuthority("agent:read"))
                : List.of(new SimpleGrantedAuthority("agent:read"));
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, userId, "USER", authorities);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        doReturn(authorities).when(authentication).getAuthorities();
        return authentication;
    }
}
