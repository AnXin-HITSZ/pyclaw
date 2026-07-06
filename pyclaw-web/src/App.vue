<template>
  <div class="app">
    <aside v-if="state.me" class="sidebar">
      <div class="brand">
        <span class="brand-mark">P</span>
        <div>
          <strong>pyclaw</strong>
          <small>Console</small>
        </div>
      </div>
      <nav>
        <button
          v-for="item in visibleNav"
          :key="item.key"
          :class="{ active: state.view === item.key }"
          @click="setView(item.key)"
        >
          <span>{{ item.icon }}</span>
          {{ item.label }}
        </button>
      </nav>
    </aside>

    <main :class="['main', { centered: !state.me }]">
      <section v-if="!state.me" class="login-panel">
        <div class="login-copy">
          <p class="eyebrow">pyclaw Console</p>
          <h1>登录管理控制台</h1>
          <p>通过 Spring Backend 统一鉴权后访问 Agent、Provider、Channel、审计与用量接口。</p>
        </div>
        <form class="panel form-grid" @submit.prevent="login">
          <label>
            后端地址
            <input v-model="state.apiBase" placeholder="留空表示同源，例如 K3s Ingress" />
          </label>
          <label>
            用户名
            <input v-model="loginForm.username" autocomplete="username" />
          </label>
          <label>
            密码
            <input v-model="loginForm.password" type="password" autocomplete="current-password" />
          </label>
          <button class="primary" type="submit" :disabled="state.loading">
            {{ state.loading ? "登录中" : "登录" }}
          </button>
          <p v-if="state.error" class="error">{{ state.error }}</p>
        </form>
      </section>

      <template v-else>
        <header class="topbar">
          <div>
            <p class="eyebrow">{{ currentTitle }}</p>
            <h1>{{ currentSubtitle }}</h1>
          </div>
          <div class="userbox">
            <span>{{ state.me.username }}</span>
            <small>{{ state.me.actorType }}</small>
            <button class="ghost" @click="logout">退出</button>
          </div>
        </header>

        <div v-if="state.error" class="toast error">
          {{ state.error }}
          <button @click="state.error = ''">关闭</button>
        </div>
        <div v-if="state.notice" class="toast success">
          {{ state.notice }}
          <button @click="state.notice = ''">关闭</button>
        </div>

        <section v-if="state.view === 'dashboard'" class="stack">
          <div class="metric-grid">
            <article class="metric">
              <span>后端健康</span>
              <strong>{{ dashboard.health }}</strong>
            </article>
            <article class="metric">
              <span>当前用户</span>
              <strong>{{ state.me.username }}</strong>
            </article>
            <article class="metric">
              <span>用量记录</span>
              <strong>{{ usageStats.totalRuns }}</strong>
            </article>
            <article class="metric">
              <span>总 Tokens</span>
              <strong>{{ usageStats.totalTokens }}</strong>
            </article>
          </div>
          <div class="panel">
            <div class="panel-title">
              <h2>权限</h2>
              <button @click="refreshDashboard">刷新</button>
            </div>
            <div class="chips">
              <span v-for="authority in state.me.authorities" :key="authority">{{ authority }}</span>
            </div>
          </div>
        </section>

        <section v-if="state.view === 'agent'" class="two-column">
          <form class="panel form-grid" @submit.prevent="runAgent">
            <div class="panel-title">
              <h2>Agent Playground</h2>
              <button class="primary" type="submit" :disabled="state.loading">
                {{ state.loading ? "运行中" : "运行" }}
              </button>
            </div>
            <label class="wide">
              Prompt
              <textarea v-model="agentForm.prompt" rows="8" />
            </label>
            <label>
              Provider
              <input v-model="agentForm.provider" />
            </label>
            <label>
              Model
              <input v-model="agentForm.model" placeholder="可留空" />
            </label>
            <label>
              Session ID
              <input v-model="agentForm.sessionId" />
            </label>
            <label>
              Tool Profile
              <select v-model="agentForm.toolProfile">
                <option>minimal</option>
                <option>readonly</option>
                <option>coding</option>
                <option>messaging</option>
                <option>full</option>
              </select>
            </label>
          </form>
          <article class="panel result-panel">
            <div class="panel-title">
              <h2>响应</h2>
              <span v-if="agentResult.latencyMs">{{ agentResult.latencyMs }} ms</span>
            </div>
            <pre class="answer">{{ agentResult.text || "等待调用结果" }}</pre>
            <details>
              <summary>原始 JSON</summary>
              <pre>{{ pretty(agentResult.raw) }}</pre>
            </details>
          </article>
        </section>

        <section v-if="state.view === 'tokens'" class="stack">
          <form class="panel form-grid" @submit.prevent="createToken">
            <div class="panel-title">
              <h2>创建 API Token</h2>
              <button class="primary" type="submit">创建</button>
            </div>
            <label>
              名称
              <input v-model="tokenForm.name" />
            </label>
            <label>
              过期时间
              <input v-model="tokenForm.expiresAt" placeholder="2026-12-31T23:59:59+08:00" />
            </label>
            <label class="wide">
              权限 scopes
              <input v-model="tokenForm.scopes" placeholder="agent:run,token:manage_self" />
            </label>
          </form>
          <DataTable title="API Tokens" :rows="tokens" :columns="tokenColumns">
            <template #actions="{ row }">
              <button class="danger" :disabled="Boolean(row.revokedAt)" @click="revokeToken(row.id)">撤销</button>
            </template>
          </DataTable>
        </section>

        <section v-if="state.view === 'users'" class="stack">
          <form class="panel form-grid" @submit.prevent="createUser">
            <div class="panel-title">
              <h2>创建用户</h2>
              <button class="primary" type="submit">创建</button>
            </div>
            <label>
              用户名
              <input v-model="userForm.username" />
            </label>
            <label>
              密码
              <input v-model="userForm.password" type="password" />
            </label>
            <label>
              显示名
              <input v-model="userForm.displayName" />
            </label>
            <label class="wide">
              权限
              <input v-model="userForm.authorities" />
            </label>
          </form>
          <DataTable title="Users" :rows="users" :columns="userColumns">
            <template #actions="{ row }">
              <button class="danger" :disabled="row.status === 'DISABLED'" @click="disableUser(row.id)">禁用</button>
            </template>
          </DataTable>
        </section>

        <section v-if="state.view === 'providers'" class="stack">
          <form class="panel form-grid" @submit.prevent="saveProvider">
            <div class="panel-title">
              <h2>{{ providerForm.id ? "编辑 Provider" : "创建 Provider" }}</h2>
              <div>
                <button v-if="providerForm.id" type="button" @click="resetProviderForm">取消编辑</button>
                <button class="primary" type="submit">保存</button>
              </div>
            </div>
            <label>
              名称
              <input v-model="providerForm.name" />
            </label>
            <label>
              类型
              <input v-model="providerForm.providerType" />
            </label>
            <label>
              Base URL
              <input v-model="providerForm.baseUrl" />
            </label>
            <label>
              Model
              <input v-model="providerForm.model" />
            </label>
            <label>
              API Mode
              <select v-model="providerForm.apiMode">
                <option>chat_completions</option>
                <option>responses</option>
              </select>
            </label>
            <label>
              Secret Ref
              <input v-model="providerForm.secretRef" />
            </label>
            <label class="checkline">
              <input v-model="providerForm.enabled" type="checkbox" />
              启用
            </label>
          </form>
          <DataTable title="Providers" :rows="providers" :columns="providerColumns">
            <template #actions="{ row }">
              <button @click="editProvider(row)">编辑</button>
              <button class="danger" @click="deleteProvider(row.id)">删除</button>
            </template>
          </DataTable>
        </section>

        <section v-if="state.view === 'channels'" class="stack">
          <form class="panel form-grid" @submit.prevent="saveChannel">
            <div class="panel-title">
              <h2>{{ channelForm.id ? "编辑 Channel" : "创建 Channel" }}</h2>
              <div>
                <button v-if="channelForm.id" type="button" @click="resetChannelForm">取消编辑</button>
                <button class="primary" type="submit">保存</button>
              </div>
            </div>
            <label>
              类型
              <select v-model="channelForm.channelType">
                <option>wechat</option>
                <option>feishu</option>
              </select>
            </label>
            <label>
              名称
              <input v-model="channelForm.name" />
            </label>
            <label>
              Secret Ref
              <input v-model="channelForm.secretRef" />
            </label>
            <label class="checkline">
              <input v-model="channelForm.enabled" type="checkbox" />
              启用
            </label>
            <label class="wide">
              Config JSON
              <textarea v-model="channelForm.configJson" rows="7" />
            </label>
          </form>
          <DataTable title="Channels" :rows="channels" :columns="channelColumns">
            <template #actions="{ row }">
              <button @click="editChannel(row)">编辑</button>
              <button class="danger" @click="deleteChannel(row.id)">删除</button>
            </template>
          </DataTable>
        </section>

        <section v-if="state.view === 'audit'" class="stack">
          <DataTable title="Audit Logs" :rows="auditLogs" :columns="auditColumns" />
        </section>

        <section v-if="state.view === 'usage'" class="stack">
          <div class="metric-grid">
            <article class="metric">
              <span>调用次数</span>
              <strong>{{ usageStats.totalRuns }}</strong>
            </article>
            <article class="metric">
              <span>成功率</span>
              <strong>{{ usageStats.successRate }}%</strong>
            </article>
            <article class="metric">
              <span>总 Tokens</span>
              <strong>{{ usageStats.totalTokens }}</strong>
            </article>
            <article class="metric">
              <span>平均延迟</span>
              <strong>{{ usageStats.avgLatency }} ms</strong>
            </article>
          </div>
          <DataTable title="Usage Records" :rows="usageRecords" :columns="usageColumns" />
        </section>
      </template>
    </main>

    <div v-if="createdToken.token" class="modal-backdrop">
      <section class="modal">
        <p class="eyebrow">API Token 只显示一次</p>
        <h2>{{ createdToken.tokenId }}</h2>
        <pre>{{ createdToken.token }}</pre>
        <div class="modal-actions">
          <button @click="copy(createdToken.token)">复制</button>
          <button class="primary" @click="createdToken.token = ''">我已保存</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";

const TOKEN_KEY = "pyclaw.console.token";
const BASE_KEY = "pyclaw.console.baseUrl";

const nav = [
  { key: "dashboard", label: "概览", icon: "01" },
  { key: "agent", label: "Agent", icon: "02", authority: "agent:run" },
  { key: "tokens", label: "API Tokens", icon: "03", authority: "token:manage_self" },
  { key: "users", label: "用户", icon: "04", authority: "user:manage" },
  { key: "providers", label: "Providers", icon: "05", authority: "provider:manage" },
  { key: "channels", label: "Channels", icon: "06", authority: "channel:manage" },
  { key: "audit", label: "审计", icon: "07", authority: "audit:read" },
  { key: "usage", label: "用量", icon: "08", authority: "audit:read" }
];

const state = reactive({
  apiBase: localStorage.getItem(BASE_KEY) || "",
  token: localStorage.getItem(TOKEN_KEY) || "",
  me: null,
  view: "dashboard",
  loading: false,
  error: "",
  notice: ""
});

const loginForm = reactive({ username: "admin", password: "" });
const agentForm = reactive({
  prompt: "你好，请用一句话介绍 pyclaw。",
  provider: "openai",
  sessionId: "web-demo",
  toolProfile: "minimal",
  model: ""
});
const tokenForm = reactive({ name: "frontend-token", expiresAt: "", scopes: "agent:run" });
const userForm = reactive({
  username: "",
  password: "",
  displayName: "",
  authorities: "agent:run,token:manage_self"
});
const providerForm = reactive(defaultProviderForm());
const channelForm = reactive(defaultChannelForm());

const dashboard = reactive({ health: "unknown" });
const agentResult = reactive({ text: "", latencyMs: 0, raw: null });
const createdToken = reactive({ tokenId: "", token: "" });

const tokens = ref([]);
const users = ref([]);
const providers = ref([]);
const channels = ref([]);
const auditLogs = ref([]);
const usageRecords = ref([]);

const visibleNav = computed(() => nav.filter((item) => !item.authority || has(item.authority)));
const currentTitle = computed(() => nav.find((item) => item.key === state.view)?.label || "Console");
const currentSubtitle = computed(() => {
  const map = {
    dashboard: "系统运行状态",
    agent: "调用 pyclaw Agent",
    tokens: "管理个人或管理员 API Token",
    users: "用户与权限",
    providers: "模型 Provider 配置",
    channels: "微信与飞书 Channel 配置",
    audit: "安全审计记录",
    usage: "Agent 调用用量"
  };
  return map[state.view] || "pyclaw Console";
});

const tokenColumns = ["name", "scopes", "expiresAt", "revokedAt", "createdAt", "lastUsedAt"];
const userColumns = ["username", "displayName", "status", "authorities", "createdAt", "updatedAt"];
const providerColumns = ["name", "providerType", "baseUrl", "model", "apiMode", "secretRef", "enabled"];
const channelColumns = ["channelType", "name", "configJson", "secretRef", "enabled", "updatedAt"];
const auditColumns = ["createdAt", "actorType", "actorId", "action", "resourceType", "resourceId", "success", "errorMessage"];
const usageColumns = ["createdAt", "userId", "sessionId", "provider", "model", "totalTokens", "success", "latencyMs"];

const usageStats = computed(() => {
  const rows = usageRecords.value;
  const totalRuns = rows.length;
  const success = rows.filter((item) => item.success).length;
  const totalTokens = rows.reduce((sum, item) => sum + Number(item.totalTokens || 0), 0);
  const latencyRows = rows.filter((item) => Number.isFinite(Number(item.latencyMs)));
  const avgLatency = latencyRows.length
    ? Math.round(latencyRows.reduce((sum, item) => sum + Number(item.latencyMs || 0), 0) / latencyRows.length)
    : 0;
  const successRate = totalRuns ? Math.round((success / totalRuns) * 100) : 0;
  return { totalRuns, successRate, totalTokens, avgLatency };
});

onMounted(async () => {
  if (state.token) {
    await loadMe();
  }
});

function defaultProviderForm() {
  return {
    id: "",
    name: "",
    providerType: "openai-compatible",
    baseUrl: "",
    model: "deepseek-chat",
    apiMode: "chat_completions",
    secretRef: "pyclaw-provider-secret",
    enabled: true
  };
}

function defaultChannelForm() {
  return {
    id: "",
    channelType: "wechat",
    name: "",
    configJson: '{\n  "callbackPath": "/api/webhooks/wechat"\n}',
    secretRef: "",
    enabled: true
  };
}

function has(authority) {
  return Boolean(state.me?.authorities?.includes(authority));
}

function endpoint(path) {
  const base = state.apiBase.trim().replace(/\/$/, "");
  return `${base}${path}`;
}

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (!(options.body instanceof FormData)) {
    headers["Content-Type"] = headers["Content-Type"] || "application/json";
  }
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  const res = await fetch(endpoint(path), { ...options, headers });
  if (res.status === 401 || res.status === 403) {
    if (path !== "/api/auth/me") {
      throw new Error("未登录、登录已过期或权限不足");
    }
  }
  if (!res.ok) {
    const message = await readError(res);
    throw new Error(message);
  }
  if (res.status === 204) {
    return null;
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function readError(res) {
  const text = await res.text();
  if (!text) {
    return `${res.status} ${res.statusText}`;
  }
  try {
    return JSON.parse(text).message || text;
  } catch {
    return text;
  }
}

async function login() {
  await withLoading(async () => {
    localStorage.setItem(BASE_KEY, state.apiBase.trim());
    const data = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: loginForm.username, password: loginForm.password })
    });
    state.token = data.accessToken;
    localStorage.setItem(TOKEN_KEY, state.token);
    await loadMe();
    notice("登录成功");
  });
}

async function loadMe() {
  try {
    state.me = await api("/api/auth/me");
    if (!visibleNav.value.some((item) => item.key === state.view)) {
      state.view = "dashboard";
    }
    await refreshCurrent();
  } catch (error) {
    state.me = null;
    state.token = "";
    localStorage.removeItem(TOKEN_KEY);
    state.error = error.message;
  }
}

function logout() {
  state.me = null;
  state.token = "";
  localStorage.removeItem(TOKEN_KEY);
}

async function setView(view) {
  state.view = view;
  await refreshCurrent();
}

async function refreshCurrent() {
  const tasks = {
    dashboard: refreshDashboard,
    tokens: loadTokens,
    users: loadUsers,
    providers: loadProviders,
    channels: loadChannels,
    audit: loadAudit,
    usage: loadUsage
  };
  if (tasks[state.view]) {
    await tasks[state.view]();
  }
}

async function refreshDashboard() {
  await withError(async () => {
    try {
      const healthPath = state.apiBase.trim() ? "/healthz" : "/backend-healthz";
      const data = await api(healthPath);
      dashboard.health = data?.status || "ok";
    } catch {
      dashboard.health = "unavailable";
    }
    if (has("audit:read")) {
      await loadUsage();
    }
  });
}

async function runAgent() {
  await withLoading(async () => {
    const payload = {
      prompt: agentForm.prompt,
      provider: agentForm.provider || undefined,
      sessionId: agentForm.sessionId || undefined,
      toolProfile: agentForm.toolProfile || undefined,
      model: agentForm.model || undefined
    };
    const data = await api("/api/agent/run", { method: "POST", body: JSON.stringify(payload) });
    agentResult.text = data.text || "";
    agentResult.latencyMs = data.latencyMs || 0;
    agentResult.raw = data;
  });
}

async function loadTokens() {
  await withError(async () => {
    tokens.value = sanitizeRows(await api("/api/tokens"));
  });
}

async function createToken() {
  await withLoading(async () => {
    const data = await api("/api/tokens", {
      method: "POST",
      body: JSON.stringify({
        name: tokenForm.name,
        expiresAt: tokenForm.expiresAt || null,
        scopes: splitCsv(tokenForm.scopes)
      })
    });
    createdToken.tokenId = data.tokenId;
    createdToken.token = data.token;
    await loadTokens();
  });
}

async function revokeToken(id) {
  if (!confirm("确认撤销这个 API Token？")) return;
  await withLoading(async () => {
    await api(`/api/tokens/${id}`, { method: "DELETE" });
    await loadTokens();
    notice("Token 已撤销");
  });
}

async function loadUsers() {
  await withError(async () => {
    users.value = sanitizeRows(await api("/api/users"));
  });
}

async function createUser() {
  await withLoading(async () => {
    await api("/api/users", {
      method: "POST",
      body: JSON.stringify({
        username: userForm.username,
        password: userForm.password,
        displayName: userForm.displayName,
        authorities: userForm.authorities
      })
    });
    Object.assign(userForm, { username: "", password: "", displayName: "", authorities: userForm.authorities });
    await loadUsers();
    notice("用户已创建");
  });
}

async function disableUser(id) {
  if (!confirm("确认禁用这个用户？")) return;
  await withLoading(async () => {
    await api(`/api/users/${id}/disable`, { method: "PUT" });
    await loadUsers();
  });
}

async function loadProviders() {
  await withError(async () => {
    providers.value = await api("/api/providers");
  });
}

function editProvider(row) {
  Object.assign(providerForm, row);
}

function resetProviderForm() {
  Object.assign(providerForm, defaultProviderForm());
}

async function saveProvider() {
  await withLoading(async () => {
    const payload = {
      name: providerForm.name,
      providerType: providerForm.providerType,
      baseUrl: providerForm.baseUrl || null,
      model: providerForm.model,
      apiMode: providerForm.apiMode,
      secretRef: providerForm.secretRef || null,
      enabled: providerForm.enabled
    };
    const path = providerForm.id ? `/api/providers/${providerForm.id}` : "/api/providers";
    const method = providerForm.id ? "PUT" : "POST";
    await api(path, { method, body: JSON.stringify(payload) });
    resetProviderForm();
    await loadProviders();
    notice("Provider 已保存");
  });
}

async function deleteProvider(id) {
  if (!confirm("确认删除这个 Provider？")) return;
  await withLoading(async () => {
    await api(`/api/providers/${id}`, { method: "DELETE" });
    await loadProviders();
  });
}

async function loadChannels() {
  await withError(async () => {
    channels.value = await api("/api/channels");
  });
}

function editChannel(row) {
  Object.assign(channelForm, {
    ...row,
    configJson: formatConfig(row.configJson)
  });
}

function resetChannelForm() {
  Object.assign(channelForm, defaultChannelForm());
}

async function saveChannel() {
  await withLoading(async () => {
    const payload = {
      channelType: channelForm.channelType,
      name: channelForm.name,
      config: parseConfig(channelForm.configJson),
      secretRef: channelForm.secretRef || null,
      enabled: channelForm.enabled
    };
    const path = channelForm.id ? `/api/channels/${channelForm.id}` : "/api/channels";
    const method = channelForm.id ? "PUT" : "POST";
    await api(path, { method, body: JSON.stringify(payload) });
    resetChannelForm();
    await loadChannels();
    notice("Channel 已保存");
  });
}

async function deleteChannel(id) {
  if (!confirm("确认删除这个 Channel？")) return;
  await withLoading(async () => {
    await api(`/api/channels/${id}`, { method: "DELETE" });
    await loadChannels();
  });
}

async function loadAudit() {
  await withError(async () => {
    auditLogs.value = await api("/api/audit-logs");
  });
}

async function loadUsage() {
  await withError(async () => {
    usageRecords.value = await api("/api/usage-records");
  });
}

function splitCsv(value) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function parseConfig(value) {
  try {
    return value.trim() ? JSON.parse(value) : {};
  } catch {
    throw new Error("Config JSON 格式不正确");
  }
}

function formatConfig(value) {
  if (!value) return "{}";
  try {
    return JSON.stringify(typeof value === "string" ? JSON.parse(value) : value, null, 2);
  } catch {
    return value;
  }
}

function sanitizeRows(rows) {
  return (rows || []).map((row) => {
    const copy = { ...row };
    delete copy.passwordHash;
    delete copy.tokenHash;
    return copy;
  });
}

function pretty(value) {
  return value ? JSON.stringify(value, null, 2) : "{}";
}

async function copy(value) {
  await navigator.clipboard?.writeText(value);
  notice("已复制");
}

async function withLoading(fn) {
  state.loading = true;
  await withError(fn);
  state.loading = false;
}

async function withError(fn) {
  state.error = "";
  try {
    await fn();
  } catch (error) {
    state.error = error.message || String(error);
  } finally {
    state.loading = false;
  }
}

function notice(message) {
  state.notice = message;
  setTimeout(() => {
    if (state.notice === message) state.notice = "";
  }, 3000);
}
</script>

<script>
export default {
  components: {
    DataTable: {
      props: {
        title: { type: String, required: true },
        rows: { type: Array, required: true },
        columns: { type: Array, required: true }
      },
      template: `
        <article class="panel table-panel">
          <div class="panel-title">
            <h2>{{ title }}</h2>
            <span>{{ rows.length }} 条</span>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th v-for="column in columns" :key="column">{{ column }}</th>
                  <th v-if="$slots.actions">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!rows.length">
                  <td :colspan="columns.length + ($slots.actions ? 1 : 0)" class="empty">暂无数据</td>
                </tr>
                <tr v-for="row in rows" :key="row.id || JSON.stringify(row)">
                  <td v-for="column in columns" :key="column">
                    <code v-if="typeof row[column] === 'boolean'">{{ row[column] }}</code>
                    <span v-else>{{ cell(row[column]) }}</span>
                  </td>
                  <td v-if="$slots.actions" class="actions">
                    <slot name="actions" :row="row" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      `,
      methods: {
        cell(value) {
          if (value === null || value === undefined || value === "") return "-";
          if (typeof value === "object") return JSON.stringify(value);
          return String(value);
        }
      }
    }
  }
};
</script>

<style>
:root {
  color: #18212f;
  background: #f4f6f8;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 15px;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
}

button,
input,
select,
textarea {
  font: inherit;
}

button {
  border: 1px solid #c8d0da;
  border-radius: 6px;
  background: #ffffff;
  color: #253246;
  cursor: pointer;
  padding: 0.55rem 0.8rem;
}

button:hover {
  border-color: #6f8199;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.primary {
  background: #116a5b;
  border-color: #116a5b;
  color: #ffffff;
}

.danger {
  color: #9f1d20;
  border-color: #e4b6b6;
}

.ghost {
  background: transparent;
}

.app {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 240px 1fr;
}

.sidebar {
  background: #17202d;
  color: #e6edf5;
  padding: 1.25rem 1rem;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.35rem 0.25rem 1.25rem;
}

.brand-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #36a38f;
  color: #ffffff;
  font-weight: 800;
}

.brand small {
  display: block;
  color: #93a4b8;
}

nav {
  display: grid;
  gap: 0.35rem;
}

nav button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  background: transparent;
  border-color: transparent;
  color: #cbd5e1;
  text-align: left;
}

nav button span {
  width: 30px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 5px;
  background: #263447;
  font-size: 0.78rem;
}

nav button.active,
nav button:hover {
  background: #263447;
  color: #ffffff;
}

.main {
  padding: 1.25rem;
  overflow: auto;
}

.main.centered {
  grid-column: 1 / -1;
  display: grid;
  place-items: center;
}

.login-panel {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: 1fr 390px;
  gap: 1.25rem;
  align-items: center;
}

.login-copy h1,
.topbar h1 {
  margin: 0;
  letter-spacing: 0;
}

.eyebrow {
  margin: 0 0 0.3rem;
  color: #607083;
  font-size: 0.82rem;
  text-transform: uppercase;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.userbox {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  background: #ffffff;
  border: 1px solid #dce2ea;
  border-radius: 8px;
  padding: 0.5rem;
}

.userbox small {
  color: #607083;
}

.stack {
  display: grid;
  gap: 1rem;
}

.two-column {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(420px, 1.1fr);
  gap: 1rem;
}

.panel {
  background: #ffffff;
  border: 1px solid #dce2ea;
  border-radius: 8px;
  padding: 1rem;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: center;
  margin-bottom: 1rem;
}

.panel-title h2 {
  margin: 0;
  font-size: 1rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.85rem;
}

.form-grid .panel-title,
.wide {
  grid-column: 1 / -1;
}

label {
  display: grid;
  gap: 0.35rem;
  color: #405066;
  font-size: 0.9rem;
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid #c8d0da;
  border-radius: 6px;
  color: #17202d;
  background: #ffffff;
  padding: 0.6rem 0.7rem;
  resize: vertical;
}

.checkline {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkline input {
  width: auto;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.metric {
  background: #ffffff;
  border: 1px solid #dce2ea;
  border-radius: 8px;
  padding: 1rem;
}

.metric span {
  display: block;
  color: #607083;
  margin-bottom: 0.45rem;
}

.metric strong {
  font-size: 1.45rem;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.chips span {
  border: 1px solid #c8d0da;
  border-radius: 999px;
  padding: 0.35rem 0.65rem;
  background: #f7fafc;
}

.table-wrap {
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 860px;
}

th,
td {
  border-bottom: 1px solid #e4e9ef;
  padding: 0.65rem;
  text-align: left;
  vertical-align: top;
}

th {
  color: #607083;
  font-size: 0.82rem;
  font-weight: 700;
  background: #f8fafc;
}

td {
  max-width: 320px;
  overflow-wrap: anywhere;
}

.actions {
  white-space: nowrap;
}

.actions button + button {
  margin-left: 0.4rem;
}

.empty {
  text-align: center;
  color: #607083;
}

pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #111827;
  color: #e5e7eb;
  border-radius: 8px;
  padding: 1rem;
  max-height: 520px;
  overflow: auto;
}

.answer {
  min-height: 260px;
}

details {
  margin-top: 1rem;
}

summary {
  cursor: pointer;
  color: #405066;
  margin-bottom: 0.5rem;
}

.toast {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  border-radius: 8px;
  padding: 0.8rem 1rem;
  margin-bottom: 1rem;
}

.error {
  color: #9f1d20;
}

.toast.error {
  background: #fff1f1;
  border: 1px solid #e4b6b6;
}

.toast.success {
  background: #edf8f4;
  border: 1px solid #abd8cc;
  color: #116a5b;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgba(15, 23, 42, 0.45);
}

.modal {
  width: min(640px, 100%);
  background: #ffffff;
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);
}

.modal h2 {
  margin-top: 0;
  font-size: 1rem;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 1rem;
}

@media (max-width: 960px) {
  .app {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
  }

  nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .login-panel,
  .two-column,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .topbar {
    align-items: stretch;
    flex-direction: column;
  }

  .userbox {
    justify-content: space-between;
  }
}
</style>
