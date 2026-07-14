<template>
  <div class="page">
    <div class="page-header">
      <h1>Claw 管理</h1>
      <button class="btn-primary" @click="showCreate = true">+ 新建 Claw</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <div v-else class="claw-grid">
      <div v-for="claw in claws" :key="claw.id" class="claw-card" @click="goDetail(claw.id)">
        <div class="claw-card-header">
          <h3>{{ claw.name }}</h3>
          <span class="claw-status" :class="claw.status">{{ claw.status }}</span>
        </div>
        <p class="claw-desc">{{ claw.description || "暂无描述" }}</p>
        <div class="claw-meta">
          <span>{{ claw.roles?.length || 0 }} 个 Agent 角色</span>
          <span v-if="claw.feishuEnabled">飞书已启用</span>
        </div>
        <div class="claw-actions" @click.stop>
          <button class="btn-sm btn-danger" @click="handleDelete(claw)">删除</button>
        </div>
      </div>

      <div v-if="claws.length === 0" class="empty">
        <p>还没有 Claw，创建一个开始吧</p>
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
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h1 { font-size: 24px; }
.claw-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; }
.claw-card {
  background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px;
  padding: 20px; cursor: pointer; transition: border-color 0.2s;
}
.claw-card:hover { border-color: var(--accent); }
.claw-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.claw-card-header h3 { font-size: 16px; }
.claw-status { font-size: 11px; padding: 2px 8px; border-radius: 10px; text-transform: uppercase; }
.claw-status.active { background: rgba(63, 185, 80, 0.15); color: var(--success); }
.claw-desc { color: var(--text-secondary); font-size: 13px; margin-bottom: 12px; }
.claw-meta { display: flex; gap: 16px; font-size: 12px; color: var(--text-muted); }
.claw-actions { margin-top: 12px; }
.btn-sm { padding: 4px 12px; font-size: 12px; border-radius: 4px; border: 1px solid var(--border-color); background: transparent; }
.btn-danger { color: var(--danger); border-color: var(--danger); }
.btn-primary { padding: 8px 20px; font-size: 14px; font-weight: 600; color: #fff; background: var(--accent); border: none; border-radius: 6px; }
.btn-secondary { padding: 8px 20px; font-size: 14px; color: var(--text-secondary); background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: 6px; }
.loading, .error-msg, .empty { text-align: center; padding: 48px; color: var(--text-secondary); }
.error-msg { color: var(--danger); }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 12px; padding: 32px; width: 480px; max-width: 90vw; }
.modal h2 { margin-bottom: 20px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 13px; color: var(--text-secondary); }
.form-group input, .form-group textarea, .form-group select {
  width: 100%; padding: 8px 12px; background: var(--bg-primary); border: 1px solid var(--border-color);
  border-radius: 6px; color: var(--text-primary); font-size: 14px;
}
.form-group input:focus, .form-group textarea:focus, .form-group select:focus { outline: none; border-color: var(--accent); }
.checkbox-label { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: 14px; color: var(--text-primary); }
.checkbox-label input { width: auto; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
