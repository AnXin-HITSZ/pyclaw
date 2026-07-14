<template>
  <div class="page">
    <div class="page-header">
      <h1>Claw 管理</h1>
      <button class="btn-primary" @click="showCreate = true">+ 新建 Claw</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <div v-else>
      <!-- Stat Summary -->
      <div class="stat-row">
        <div class="stat-card accent">
          <div class="stat-value">{{ claws.length }}</div>
          <div class="stat-label">Claw 总数</div>
        </div>
        <div class="stat-card success">
          <div class="stat-value">{{ claws.filter(c => c.status === 'active').length }}</div>
          <div class="stat-label">运行中</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ claws.reduce((sum, c) => sum + (c.roles?.length || 0), 0) }}</div>
          <div class="stat-label">Agent 角色</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ claws.filter(c => c.feishuEnabled).length }}</div>
          <div class="stat-label">飞书已绑定</div>
        </div>
      </div>

      <div class="claw-grid">
      <TransitionGroup name="stagger">
        <div v-for="(claw, i) in claws" :key="claw.id" class="card claw-card"
             :style="{ transitionDelay: `${i * 60}ms` }"
             @click="goDetail(claw.id)">
          <div class="claw-card-header">
            <h3>{{ claw.name }}</h3>
            <span class="status-tag" :class="claw.status">{{ claw.status }}</span>
          </div>
          <p class="claw-desc">{{ claw.description || "暂无描述" }}</p>
          <div class="claw-meta">
            <span class="meta-item">
              <span class="meta-dot"></span>
              {{ claw.roles?.length || 0 }} 个 Agent 角色
            </span>
            <span v-if="claw.feishuEnabled" class="meta-item">
              <span class="meta-dot feishu"></span>飞书已启用
            </span>
          </div>
          <div class="claw-actions" @click.stop>
            <button class="btn-delete" @click="handleDelete(claw)">删除</button>
          </div>
        </div>
      </TransitionGroup>

      <div v-if="claws.length === 0 && !loading" class="empty-state">
        <div class="empty-state-icon">🦀</div>
        <h3>还没有任何 Claw</h3>
        <p>Claw 是你专属的 AI 工作空间，每个 Claw 拥有独立的沙箱环境、Agent 角色和会话历史。</p>
        <button class="btn-primary" @click="showCreate = true">+ 创建第一个 Claw</button>
      </div>
      </div>
    </div>

    <!-- Create Modal -->
    <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
      <div class="modal">
        <h2>新建 Claw</h2>
        <form @submit.prevent="handleCreate">
          <div class="form-group">
            <label>名称 *</label>
            <input v-model="createForm.name" required placeholder="我的 Claw" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="createForm.description" rows="3" placeholder="可选描述..." />
          </div>
          <div class="form-group">
            <label>默认 Agent</label>
            <select v-model="createForm.defaultAgentId">
              <option value="">不选择</option>
              <option v-for="a in agents" :key="a.id" :value="a.id">{{ a.name }} ({{ a.agentKey }})</option>
            </select>
          </div>
          <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="createForm.feishuEnabled" />
              启用飞书
            </label>
          </div>
          <div v-if="createForm.feishuEnabled" class="form-group">
            <label>飞书 Peer ID</label>
            <input v-model="createForm.feishuPeerId" placeholder="群聊 ID 或用户 ID" />
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="showCreate = false">取消</button>
            <button type="submit" class="btn-primary">创建</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { api } from "../api/client.js";

const router = useRouter();
const claws = ref([]);
const agents = ref([]);
const loading = ref(true);
const error = ref("");
const showCreate = ref(false);
const createForm = ref({
  name: "", description: "", defaultAgentId: "", feishuEnabled: false, feishuPeerId: "",
});

async function load() {
  loading.value = true;
  try {
    const [c, a] = await Promise.all([
      api.get("/api/claws"),
      api.get("/api/agents"),
    ]);
    claws.value = c;
    agents.value = a;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function handleCreate() {
  try {
    await api.post("/api/claws", {
      name: createForm.value.name,
      description: createForm.value.description || undefined,
      defaultAgentId: createForm.value.defaultAgentId || undefined,
      feishuEnabled: createForm.value.feishuEnabled,
      feishuPeerId: createForm.value.feishuPeerId || undefined,
    });
    showCreate.value = false;
    createForm.value = { name: "", description: "", defaultAgentId: "", feishuEnabled: false, feishuPeerId: "" };
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

onMounted(load);
</script>

<style scoped>
.page { max-width: 1200px; }

.claw-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 20px; }

.claw-card { cursor: pointer; }
.claw-card h3 { font-size: 16px; }
.claw-card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }

.claw-desc { color: var(--text-secondary); font-size: 13px; margin-bottom: 14px; line-height: 1.5; }

.claw-meta { display: flex; gap: 16px; font-size: 12px; color: var(--text-muted); }
.meta-item { display: flex; align-items: center; gap: 6px; }
.meta-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); opacity: 0.6; }
.meta-dot.feishu { background: var(--success); }

.claw-actions { margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }

.btn-delete {
  padding: 4px 14px; font-size: 12px; font-weight: 500;
  color: var(--text-muted); background: transparent;
  border: 1px solid transparent; border-radius: var(--radius-sm);
  transition: all 0.2s var(--ease-out);
}
.claw-card:hover .btn-delete { border-color: var(--border); color: var(--text-secondary); }
.btn-delete:hover { color: var(--danger) !important; border-color: var(--danger) !important; background: rgba(248,81,73,0.06); }

/* Empty state */
.empty-state { grid-column: 1 / -1; text-align: center; padding: 72px 24px; color: var(--text-muted); }
.empty-icon { font-size: 48px; margin-bottom: 16px; animation: float 3s var(--ease-in-out) infinite; }
.empty-state p { font-size: 15px; }

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

/* TransitionGroup staggered enter */
.stagger-enter-active { transition: opacity 0.4s var(--ease-out), transform 0.4s var(--ease-spring); }
.stagger-leave-active { transition: opacity 0.2s var(--ease-out), transform 0.2s var(--ease-out); position: absolute; }
.stagger-enter-from { opacity: 0; transform: translateY(20px) scale(0.98); }
.stagger-leave-to { opacity: 0; transform: translateY(-8px) scale(0.96); }
.stagger-move { transition: transform 0.3s var(--ease-out); }
</style>
