<template>
  <div class="page">
    <div class="page-header">
      <h1>用户管理</h1>
      <button class="btn-primary" @click="openCreate">+ 新建用户</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>显示名</th>
            <th>状态</th>
            <th>权限</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td class="uname">{{ u.username }}</td>
            <td>{{ u.displayName || "—" }}</td>
            <td>
              <span class="status-tag" :class="u.status === 'ACTIVE' ? 'active' : 'disabled'">
                {{ u.status === "ACTIVE" ? "活跃" : "已禁用" }}
              </span>
            </td>
            <td><code class="auth-str">{{ u.authorities }}</code></td>
            <td>{{ formatDate(u.createdAt) }}</td>
            <td>
              <button v-if="u.status === 'ACTIVE'" class="btn-sm btn-danger" @click="handleDisable(u)">禁用</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Create Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <h2>新建用户</h2>
        <form @submit.prevent="handleCreate">
          <div class="form-group">
            <label>用户名 *</label>
            <input v-model="createForm.username" required />
          </div>
          <div class="form-group">
            <label>密码 *</label>
            <input v-model="createForm.password" type="password" required />
          </div>
          <div class="form-group">
            <label>显示名</label>
            <input v-model="createForm.displayName" />
          </div>
          <div class="form-group">
            <label>权限（逗号分隔）</label>
            <input v-model="createForm.authorities" placeholder="agent:run,token:manage_self" />
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-secondary" @click="showModal = false">取消</button>
            <button type="submit" class="btn-primary">创建</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { api } from "../../api/client.js";

const users = ref([]);
const loading = ref(true);
const showModal = ref(false);
const createForm = ref({ username: "", password: "", displayName: "", authorities: "agent:run,token:manage_self" });

async function load() {
  loading.value = true;
  try { users.value = await api.get("/api/users"); } catch {}
  finally { loading.value = false; }
}

function openCreate() {
  createForm.value = { username: "", password: "", displayName: "", authorities: "agent:run,token:manage_self" };
  showModal.value = true;
}

async function handleCreate() {
  try {
    await api.post("/api/users", {
      username: createForm.value.username,
      password: createForm.value.password,
      displayName: createForm.value.displayName || undefined,
      authorities: createForm.value.authorities,
    });
    showModal.value = false;
    await load();
  } catch (e) { alert("创建失败: " + e.message); }
}

async function handleDisable(u) {
  if (!confirm(`确定禁用用户 "${u.username}"？`)) return;
  try {
    await api.put(`/api/users/${u.id}/disable`);
    await load();
  } catch (e) { alert("操作失败: " + e.message); }
}

function formatDate(s) { return s ? new Date(s).toLocaleString("zh-CN") : "—"; }

onMounted(load);
</script>

<style scoped>
.page { max-width: 1000px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h1 { font-size: 24px; }
.table-wrap { overflow-x: auto; }
.user-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.user-table th { text-align: left; padding: 10px 12px; background: var(--bg-secondary); color: var(--text-secondary); font-weight: 600; border-bottom: 1px solid var(--border-color); }
.user-table td { padding: 10px 12px; border-bottom: 1px solid var(--border-color); }
.uname { font-weight: 600; }
.status-tag { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.status-tag.active { background: rgba(63,185,80,0.15); color: var(--success); }
.status-tag.disabled { background: rgba(248,81,73,0.15); color: var(--danger); }
.auth-str { font-size: 11px; color: var(--text-muted); }
.btn-sm { padding: 4px 12px; font-size: 12px; border-radius: 4px; border: 1px solid var(--border-color); background: transparent; }
.btn-danger { color: var(--danger); border-color: var(--danger); }
.btn-primary { padding: 8px 20px; font-size: 14px; font-weight: 600; color: #fff; background: var(--accent); border: none; border-radius: 6px; }
.btn-secondary { padding: 8px 20px; font-size: 14px; color: var(--text-secondary); background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: 6px; }
.loading { text-align: center; padding: 48px; color: var(--text-secondary); }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 12px; padding: 32px; width: 440px; max-width: 90vw; }
.modal h2 { margin-bottom: 20px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 13px; color: var(--text-secondary); }
.form-group input { width: 100%; padding: 8px 12px; background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: 6px; color: var(--text-primary); font-size: 14px; }
.form-group input:focus { outline: none; border-color: var(--accent); }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
