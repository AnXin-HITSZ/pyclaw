<template>
  <div class="page">
    <div class="page-header">
      <h1>API Token</h1>
      <button class="btn-primary" @click="openCreate">+ 新建 Token</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else>
      <table v-if="tokens.length" class="token-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>Scopes</th>
            <th>创建时间</th>
            <th>过期时间</th>
            <th>最后使用</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tokens" :key="t.id">
            <td class="token-name">{{ t.name }}</td>
            <td><span class="scope-tag">{{ t.scopes }}</span></td>
            <td>{{ formatDate(t.createdAt) }}</td>
            <td>{{ t.expiresAt ? formatDate(t.expiresAt) : "永不过期" }}</td>
            <td>{{ t.lastUsedAt ? formatDate(t.lastUsedAt) : "从未使用" }}</td>
            <td>
              <span class="status-tag" :class="t.revokedAt ? 'revoked' : 'active'">
                {{ t.revokedAt ? "已撤销" : "有效" }}
              </span>
            </td>
            <td>
              <button v-if="!t.revokedAt" class="btn-sm btn-danger" @click="handleRevoke(t)">撤销</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="empty">还没有 API Token</p>
    </div>

    <!-- Create Modal -->
    <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
      <div class="modal">
        <h2>新建 API Token</h2>
        <form @submit.prevent="handleCreate">
          <div class="form-group">
            <label>名称 *</label>
            <input v-model="createForm.name" required placeholder="my-token" />
          </div>
          <div class="form-group">
            <label>过期时间（可选）</label>
            <input v-model="createForm.expiresAt" type="datetime-local" />
          </div>
          <div class="form-group">
            <label>Scopes *</label>
            <div class="scope-select">
              <label class="checkbox-label">
                <input type="checkbox" value="agent:run" v-model="createForm.scopes" /> agent:run
              </label>
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="showCreate = false">取消</button>
            <button type="submit" class="btn-primary">创建</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Token Reveal Modal -->
    <div v-if="newToken" class="modal-overlay" @click.self="newToken = null">
      <div class="modal">
        <h2>Token 已创建</h2>
        <p class="warning-text">此 Token 只展示一次，请立即复制保存：</p>
        <div class="token-reveal">
          <code>{{ newToken }}</code>
        </div>
        <button class="btn-primary" @click="newToken = null">我已保存，关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { api } from "../api/client.js";

const tokens = ref([]);
const loading = ref(true);
const showCreate = ref(false);
const newToken = ref(null);
const createForm = ref({ name: "", expiresAt: "", scopes: ["agent:run"] });

async function load() {
  loading.value = true;
  try {
    tokens.value = await api.get("/api/tokens");
  } finally {
    loading.value = false;
  }
}

async function handleCreate() {
  try {
    const body = { name: createForm.value.name, scopes: createForm.value.scopes };
    if (createForm.value.expiresAt) body.expiresAt = new Date(createForm.value.expiresAt).toISOString();
    const res = await api.post("/api/tokens", body);
    newToken.value = res.token;
    showCreate.value = false;
    createForm.value = { name: "", expiresAt: "", scopes: ["agent:run"] };
    await load();
  } catch (e) {
    alert("创建失败: " + e.message);
  }
}

async function handleRevoke(t) {
  if (!confirm(`确定撤销 Token "${t.name}"？`)) return;
  try {
    await api.delete(`/api/tokens/${t.id}`);
    await load();
  } catch (e) {
    alert("撤销失败: " + e.message);
  }
}

function formatDate(s) {
  if (!s) return "—";
  return new Date(s).toLocaleString("zh-CN");
}

onMounted(load);
</script>

<style scoped>
.page { max-width: 1000px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h1 { font-size: 24px; }
.token-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.token-table th { text-align: left; padding: 10px 12px; background: var(--bg-secondary); color: var(--text-secondary); font-weight: 600; border-bottom: 1px solid var(--border-color); }
.token-table td { padding: 10px 12px; border-bottom: 1px solid var(--border-color); }
.token-name { font-weight: 600; }
.scope-tag { font-size: 11px; padding: 1px 8px; background: rgba(88,166,255,0.1); color: var(--accent); border-radius: 10px; }
.status-tag { font-size: 11px; padding: 1px 8px; border-radius: 10px; }
.status-tag.active { background: rgba(63,185,80,0.15); color: var(--success); }
.status-tag.revoked { background: rgba(248,81,73,0.15); color: var(--danger); }
.btn-sm { padding: 4px 12px; font-size: 12px; border-radius: 4px; border: 1px solid var(--border-color); background: transparent; }
.btn-danger { color: var(--danger); border-color: var(--danger); }
.btn-primary { padding: 8px 20px; font-size: 14px; font-weight: 600; color: #fff; background: var(--accent); border: none; border-radius: 6px; }
.btn-secondary { padding: 8px 20px; font-size: 14px; color: var(--text-secondary); background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: 6px; }
.loading, .empty { text-align: center; padding: 48px; color: var(--text-secondary); }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 12px; padding: 32px; width: 480px; max-width: 90vw; }
.modal h2 { margin-bottom: 20px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 13px; color: var(--text-secondary); }
.form-group input { width: 100%; padding: 8px 12px; background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: 6px; color: var(--text-primary); font-size: 14px; }
.form-group input:focus { outline: none; border-color: var(--accent); }
.checkbox-label { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-primary); }
.checkbox-label input { width: auto; }
.scope-select { display: flex; flex-direction: column; gap: 8px; }
.warning-text { color: var(--warning); font-size: 14px; margin-bottom: 12px; }
.token-reveal { background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: 6px; padding: 16px; margin-bottom: 20px; word-break: break-all; }
.token-reveal code { color: var(--success); font-size: 13px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
