<template>
  <div class="page">
    <div class="page-header">
      <button class="btn-back" @click="$router.push('/workspace/claws')">← 返回列表</button>
      <h1>{{ claw?.name || "加载中..." }}</h1>
      <button v-if="claw?.status === 'active'" class="btn-chat" @click="$router.push(`/workspace/claws/${claw.id}/chat`)">💬 开始对话</button>
      <button class="btn-primary" @click="openEdit">编辑</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <div v-else class="detail-grid">
      <!-- Info Card -->
      <div class="card">
        <h3>基本信息</h3>
        <dl>
          <dt>状态</dt><dd><span class="status-tag" :class="claw.status">{{ claw.status }}</span></dd>
          <dt>描述</dt><dd>{{ claw.description || "—" }}</dd>
          <dt>默认 Agent</dt><dd>{{ agentsMap[claw.defaultAgentId]?.name || "—" }}</dd>
          <dt>飞书</dt><dd>{{ claw.feishuEnabled ? `已启用 (${claw.feishuPeerId})` : "未启用" }}</dd>
          <dt>创建时间</dt><dd>{{ formatDate(claw.createdAt) }}</dd>
        </dl>
      </div>

      <!-- Sandbox Card -->
      <div class="card">
        <h3>Sandbox 状态</h3>
        <div v-if="sandboxLoading" class="no-data">查询中...</div>
        <div v-else-if="sandboxError" class="no-data" style="color:var(--danger)">{{ sandboxError }}</div>
        <dl v-else>
          <dt>Health</dt><dd><span class="status-tag" :class="sandboxHealthy ? 'active' : ''">{{ sandboxHealthy ? 'Healthy' : 'Down' }}</span></dd>
          <dt>Workspace</dt><dd class="mono">{{ sandboxWorkspace || "—" }}</dd>
        </dl>
        <div style="margin-top: 14px;">
          <router-link :to="`/workspace/claws/${claw.id}/files`" class="btn-secondary" style="text-decoration:none;font-size:12px">📁 Workspace 文件</router-link>
        </div>
      </div>

      <!-- Agent Roles Card -->
      <div class="card">
        <h3>Agent 角色 ({{ claw.roles?.length || 0 }})</h3>
        <div v-if="claw.roles?.length" class="role-list">
          <div v-for="role in claw.roles" :key="role.id" class="role-item">
            <div class="role-info">
              <span class="role-name">{{ role.displayName }}</span>
              <span class="role-key">{{ role.roleKey }}</span>
              <span class="role-agent">{{ role.agentName || role.agentId }}</span>
              <span v-if="role.defaultRole" class="badge badge-accent">默认</span>
              <span v-if="!role.enabled" class="badge badge-danger">已禁用</span>
            </div>
          </div>
        </div>
        <p v-else class="no-data">无 Agent 角色</p>
      </div>

      <!-- Sessions Card -->
      <div class="card">
        <h3>会话记录</h3>
        <div v-if="sessions.length" class="session-list">
          <div v-for="s in sessions" :key="s.sessionId" class="session-item">
            <div class="session-info">
              <span class="session-agent">{{ s.agentKey }}</span>
              <span class="session-model">{{ s.model }}</span>
              <span class="session-count">{{ s.messageCount }} 条消息</span>
              <span class="session-time">{{ formatDate(s.lastActiveAt) }}</span>
            </div>
          </div>
        </div>
        <p v-else class="no-data">暂无会话记录</p>
      </div>
    </div>

    <!-- Edit Modal -->
    <div v-if="showEdit" class="modal-overlay" @click.self="showEdit = false">
      <div class="modal">
        <h2>编辑 Claw</h2>
        <form @submit.prevent="handleUpdate">
          <div class="form-group">
            <label>名称 *</label>
            <input v-model="editForm.name" required />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="editForm.description" rows="3" />
          </div>
          <div class="form-group">
            <label>默认 Agent</label>
            <select v-model="editForm.defaultAgentId">
              <option value="">不选择</option>
              <option v-for="a in allAgents" :key="a.id" :value="a.id">{{ a.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="editForm.feishuEnabled" /> 启用飞书
            </label>
          </div>
          <div v-if="editForm.feishuEnabled" class="form-group">
            <label>飞书 Peer ID</label>
            <input v-model="editForm.feishuPeerId" />
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="showEdit = false">取消</button>
            <button type="submit" class="btn-primary">保存</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api/client.js";

const route = useRoute();
const claw = ref(null);
const allAgents = ref([]);
const sessions = ref([]);
const loading = ref(true);
const error = ref("");
const showEdit = ref(false);
const editForm = ref({});
const sandboxHealthy = ref(false);
const sandboxWorkspace = ref("");
const sandboxLoading = ref(true);
const sandboxError = ref("");

const agentsMap = computed(() => {
  const map = {};
  allAgents.value.forEach(a => { map[a.id] = a; });
  return map;
});

async function load() {
  loading.value = true;
  try {
    const [c, a, s] = await Promise.all([
      api.get(`/api/claws/${route.params.id}`),
      api.get("/api/agents"),
      api.get(`/api/sessions?clawId=${route.params.id}`).catch(() => []),
    ]);
    claw.value = c;
    allAgents.value = a;
    sessions.value = s || [];

    sandboxLoading.value = true;
    try { await api.get(`/api/claws/${route.params.id}/sandbox/healthz`); sandboxHealthy.value = true; } catch { sandboxHealthy.value = false; }
    try { const w = await api.get(`/api/claws/${route.params.id}/sandbox/workspace`); sandboxWorkspace.value = typeof w === "string" ? w : JSON.stringify(w); } catch { sandboxWorkspace.value = ""; }
    sandboxLoading.value = false;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function openEdit() {
  showEdit.value = true;
  editForm.value = {
    name: claw.value.name, description: claw.value.description || "",
    defaultAgentId: claw.value.defaultAgentId || "", feishuEnabled: claw.value.feishuEnabled,
    feishuPeerId: claw.value.feishuPeerId || "",
  };
}

async function handleUpdate() {
  try {
    await api.put(`/api/claws/${route.params.id}`, {
      name: editForm.value.name, description: editForm.value.description || undefined,
      defaultAgentId: editForm.value.defaultAgentId || undefined,
      feishuEnabled: editForm.value.feishuEnabled, feishuPeerId: editForm.value.feishuPeerId || undefined,
    });
    showEdit.value = false;
    await load();
  } catch (e) { alert("更新失败: " + e.message); }
}

function formatDate(s) {
  if (!s) return "—";
  return new Date(s).toLocaleString("zh-CN");
}

onMounted(load);
</script>

<style scoped>
.page { max-width: 1000px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }

dl { display: grid; grid-template-columns: auto 1fr; gap: 8px 16px; font-size: 13px; }
dt { color: var(--text-muted); font-weight: 500; }
.mono { font-family: "JetBrains Mono", "Fira Code", monospace; font-size: 12px; color: var(--text-muted); }

.role-list { display: flex; flex-direction: column; gap: 6px; }
.role-item { padding: 10px 14px; background: var(--bg-deep); border-radius: var(--radius-sm); font-size: 13px; transition: background 0.2s var(--ease-out); }
.role-item:hover { background: var(--bg-raised); }
.role-info { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.role-name { font-weight: 600; }
.role-key { color: var(--text-muted); font-family: monospace; font-size: 12px; }
.role-agent { color: var(--text-secondary); }

.badge { font-size: 10px; padding: 1px 8px; border-radius: 8px; font-weight: 600; letter-spacing: 0.2px; }
.badge-accent { background: var(--accent-glow); color: var(--accent); }
.badge-danger { background: rgba(248,81,73,0.1); color: var(--danger); }

.session-list { display: flex; flex-direction: column; gap: 6px; }
.session-item { padding: 10px 14px; background: var(--bg-deep); border-radius: var(--radius-sm); font-size: 13px; transition: background 0.2s var(--ease-out); }
.session-item:hover { background: var(--bg-raised); }
.session-info { display: flex; gap: 16px; }
.session-agent { font-weight: 600; }
.session-model, .session-count { color: var(--text-secondary); }
.session-time { color: var(--text-muted); margin-left: auto; font-size: 12px; }

.btn-chat { padding: 8px 20px; font-size: 14px; font-weight: 600; color: #0a0e14; background: var(--success); border: none; border-radius: var(--radius-sm); transition: all 0.2s var(--ease-out); }
.btn-chat:hover { filter: brightness(1.1); transform: translateY(-1px); box-shadow: 0 4px 16px rgba(63,185,80,0.25); }
</style>
