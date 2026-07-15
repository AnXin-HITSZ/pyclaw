<template>
  <div class="claw-page">
    <div class="breadcrumb">工作台 <span>›</span> Claw 管理</div>

    <header class="hero-row">
      <div>
        <h1>Claw 管理</h1>
        <p>每个 Claw 是一个独立工作区，包含多个 Agent 角色。</p>
      </div>
      <button class="btn-primary add-button" type="button" @click="openCreate">+ 新建 Claw</button>
    </header>

    <div v-if="loading" class="loading-panel">
      <div class="skeleton title"></div>
      <div class="skeleton line"></div>
    </div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <template v-else>
      <section class="guide-band" aria-label="开始使用 PyClaw">
        <div class="guide-icon">↯</div>
        <div class="guide-copy">
          <h2>开始使用 PyClaw</h2>
          <p>Claw 是执行容器，需要添加 Agent 角色后才能开始对话。完成配置后即可在 Agent 对话中与 Agent 交互。</p>
          <div class="guide-steps">
            <span>1 创建 Claw</span>
            <span>2 添加 Agent 角色</span>
            <span>3 开始对话</span>
          </div>
        </div>
      </section>

      <section class="metric-grid" aria-label="Claw 概览">
        <div class="metric-card accent">
          <strong>{{ claws.length }}</strong>
          <span>CLAW 总数</span>
        </div>
        <div class="metric-card success">
          <strong>{{ activeCount }}</strong>
          <span>运行中</span>
        </div>
        <div class="metric-card agent">
          <strong>{{ roleCount }}</strong>
          <span>AGENT 角色</span>
        </div>
        <div class="metric-card feishu">
          <strong>{{ feishuCount }}</strong>
          <span>飞书已绑定</span>
        </div>
      </section>

      <section v-if="claws.length" class="claw-grid" aria-label="Claw 列表">
        <article
          v-for="(claw, index) in claws"
          :key="claw.id"
          class="claw-card"
          :style="{ transitionDelay: `${index * 40}ms` }"
          @click="goDetail(claw.id)"
        >
          <div class="claw-card-top">
            <div class="claw-title-row">
              <span class="claw-icon">▣</span>
              <div>
                <h3>{{ claw.name }}</h3>
                <p>{{ claw.description || 'Claw 实例' }}</p>
              </div>
            </div>
            <span class="status-pill" :class="statusClass(claw.status)">{{ claw.status || 'active' }}</span>
          </div>

          <div class="role-summary">
            <div class="role-summary-head">
              <span>AGENT 角色</span>
              <strong>{{ claw.roles?.length || 0 }}</strong>
            </div>
            <div v-if="claw.roles?.length" class="role-list">
              <span v-for="role in claw.roles.slice(0, 3)" :key="role.id" class="role-chip">
                {{ role.displayName || role.agentName || role.roleKey }}
              </span>
            </div>
            <button v-else class="add-role-box" type="button" @click.stop="goDetail(claw.id)">
              <span>＋</span>
              <strong>添加 Agent 角色</strong>
              <small>Agent 是 Claw 的执行者</small>
            </button>
          </div>

          <div class="claw-card-footer" @click.stop>
            <button class="text-button danger" type="button" @click="handleDelete(claw)">删除</button>
            <button class="text-button enter" type="button" @click="goDetail(claw.id)">进入 Claw ›</button>
          </div>
        </article>
      </section>

      <section v-else class="empty-state">
        <div class="empty-state-icon">＋</div>
        <h3>还没有 Claw</h3>
        <p>创建你的第一个 Claw，然后为它添加默认 Agent 和独立沙箱工作区。</p>
        <button class="btn-primary" type="button" @click="openCreate">+ 创建第一个 Claw</button>
      </section>
    </template>

    <div v-if="showCreate" class="modal-overlay" @click.self="closeCreate">
      <div class="modal claw-modal">
        <h2>新建 Claw</h2>
        <form @submit.prevent="handleCreate">
          <div class="form-group">
            <label>名称 *</label>
            <input v-model="createForm.name" required placeholder="我的 Claw" />
          </div>

          <div class="form-group">
            <label>描述</label>
            <textarea v-model="createForm.description" rows="3" placeholder="可选描述这个 Claw 的用途" />
          </div>

          <div class="form-group">
            <label>默认 Agent</label>
            <select v-model="createForm.defaultAgentId">
              <option value="">不选择</option>
              <option v-for="agent in agents" :key="agent.id" :value="agent.id">
                {{ agent.name }} ({{ agent.agentKey }})
              </option>
            </select>
            <p v-if="createForm.defaultAgentId" class="field-hint">该 Agent 会自动作为新 Claw 的第一个默认角色。</p>
          </div>

          <label class="toggle-line">
            <input type="checkbox" v-model="createForm.feishuEnabled" />
            <span>启用飞书</span>
          </label>

          <div v-if="createForm.feishuEnabled" class="form-group">
            <label>飞书 Peer ID</label>
            <input v-model="createForm.feishuPeerId" placeholder="群聊 ID 或用户 ID" />
          </div>

          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="closeCreate">取消</button>
            <button type="submit" class="btn-primary">创建</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { api } from "../api/client.js";

const router = useRouter();
const claws = ref([]);
const agents = ref([]);
const loading = ref(true);
const error = ref("");
const showCreate = ref(false);
const createForm = ref(emptyCreateForm());

const activeCount = computed(() => claws.value.filter(claw => (claw.status || "active") === "active").length);
const roleCount = computed(() => claws.value.reduce((sum, claw) => sum + (claw.roles?.length || 0), 0));
const feishuCount = computed(() => claws.value.filter(claw => claw.feishuEnabled).length);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [clawRows, agentRows] = await Promise.all([
      api.get("/api/claws"),
      api.get("/api/agents"),
    ]);
    claws.value = clawRows;
    agents.value = agentRows;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function emptyCreateForm() {
  return {
    name: "",
    description: "",
    defaultAgentId: "",
    feishuEnabled: false,
    feishuPeerId: "",
  };
}

function openCreate() {
  createForm.value = emptyCreateForm();
  if (agents.value.length === 1) {
    createForm.value.defaultAgentId = agents.value[0].id;
  }
  showCreate.value = true;
}

function closeCreate() {
  showCreate.value = false;
}

async function handleCreate() {
  try {
    const selectedAgent = agents.value.find(agent => agent.id === createForm.value.defaultAgentId);
    const roles = selectedAgent ? [{
      agentId: selectedAgent.id,
      roleKey: "default",
      displayName: selectedAgent.name || selectedAgent.agentKey || "Default Agent",
      mentionAliases: [],
      commandPrefixes: [],
      defaultRole: true,
      enabled: true,
      sortOrder: 0,
    }] : [];

    await api.post("/api/claws", {
      name: createForm.value.name,
      description: createForm.value.description || undefined,
      defaultAgentId: createForm.value.defaultAgentId || undefined,
      feishuEnabled: createForm.value.feishuEnabled,
      feishuPeerId: createForm.value.feishuPeerId || undefined,
      roles,
    });
    showCreate.value = false;
    createForm.value = emptyCreateForm();
    await load();
  } catch (e) {
    alert("创建失败: " + e.message);
  }
}

async function handleDelete(claw) {
  if (!confirm(`确定删除 Claw "${claw.name}"？此操作不可撤销。`)) return;
  try {
    await api.delete(`/api/claws/${claw.id}`);
    await load();
  } catch (e) {
    alert("删除失败: " + e.message);
  }
}

function goDetail(id) {
  router.push(`/workspace/claws/${id}`);
}

function statusClass(status) {
  return (status || "active").toLowerCase();
}

onMounted(load);
</script>

<style scoped>
.claw-page {
  max-width: 920px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.breadcrumb span,
.breadcrumb::first-letter {
  color: var(--accent);
}

.hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.hero-row h1 {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  letter-spacing: 0;
}

.hero-row p {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 13px;
}

.add-button {
  min-width: 112px;
}

.guide-band {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 12px;
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid rgba(240, 163, 58, 0.45);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(240, 163, 58, 0.12), rgba(240, 163, 58, 0.04));
}

.guide-icon {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  color: var(--accent);
  font-size: 18px;
  font-weight: 800;
}

.guide-copy h2 {
  margin: 0 0 4px;
  color: var(--accent);
  font-size: 14px;
}

.guide-copy p {
  margin: 0;
  color: #b39056;
  font-size: 12px;
  line-height: 1.6;
}

.guide-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 10px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.metric-card {
  min-height: 76px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: rgba(17, 22, 29, 0.86);
}

.metric-card strong {
  display: block;
  font-size: 27px;
  line-height: 1;
  font-weight: 900;
  letter-spacing: 0;
}

.metric-card span {
  display: block;
  margin-top: 10px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.metric-card.accent strong { color: var(--accent); }
.metric-card.success strong { color: var(--success); }
.metric-card.agent strong { color: #7287ff; }
.metric-card.feishu strong { color: #58a6ff; }

.claw-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}

.claw-card {
  min-height: 190px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: rgba(17, 22, 29, 0.9);
  cursor: pointer;
  transition: border-color 0.2s var(--ease-out), transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}

.claw-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-light);
  box-shadow: var(--shadow);
}

.claw-card-top {
  padding: 16px 16px 12px;
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.claw-title-row {
  min-width: 0;
  display: flex;
  gap: 10px;
}

.claw-icon {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--accent);
  background: var(--accent-glow);
}

.claw-title-row h3 {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.claw-title-row p {
  margin: 3px 0 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: 999px;
  color: var(--success);
  background: rgba(63, 185, 80, 0.13);
  font-size: 11px;
  font-weight: 800;
}

.status-pill.inactive,
.status-pill.disabled {
  color: var(--danger);
  background: rgba(248, 81, 73, 0.12);
}

.role-summary {
  padding: 0 16px 12px;
  flex: 1;
}

.role-summary-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--border);
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 800;
  text-transform: uppercase;
}

.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-chip {
  max-width: 100%;
  padding: 5px 8px;
  border-radius: 999px;
  color: var(--text-secondary);
  background: var(--bg-deep);
  border: 1px solid var(--border);
  font-size: 12px;
}

.add-role-box {
  width: 100%;
  min-height: 74px;
  display: grid;
  place-items: center;
  gap: 2px;
  border: 1px dashed var(--border-light);
  border-radius: 8px;
  color: var(--text-muted);
  background: rgba(10, 14, 20, 0.62);
  text-align: center;
}

.add-role-box span {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--bg-raised);
  color: var(--text-secondary);
}

.add-role-box strong {
  color: var(--text-secondary);
  font-size: 12px;
}

.add-role-box small {
  color: var(--text-muted);
  font-size: 11px;
}

.claw-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
}

.text-button {
  padding: 4px 0;
  border: 0;
  background: transparent;
  font-size: 12px;
  font-weight: 800;
}

.text-button.danger { color: var(--text-muted); }
.text-button.danger:hover { color: var(--danger); }
.text-button.enter { color: var(--accent); }
.text-button.enter:hover { color: var(--accent-soft); }

.loading-panel {
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-surface);
}

.loading-panel .title { width: 180px; height: 22px; margin-bottom: 16px; }
.loading-panel .line { width: 70%; height: 14px; }

.claw-modal {
  width: 480px;
}

.field-hint {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.toggle-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 2px 0 16px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.toggle-line input {
  width: 14px;
  height: 14px;
  accent-color: var(--accent);
}

.empty-state {
  margin-top: 24px;
  padding: 58px 24px;
  border: 1px dashed var(--border-light);
  border-radius: 8px;
  background: rgba(17, 22, 29, 0.62);
}

@media (max-width: 900px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .hero-row {
    display: grid;
  }
  .add-button {
    width: 100%;
  }
  .guide-band {
    grid-template-columns: 1fr;
  }
  .metric-grid,
  .claw-grid {
    grid-template-columns: 1fr;
  }
}
</style>
