<template>
  <div class="user-page">
    <PageHeader title="用户管理" subtitle="管理系统用户、状态与权限。">
      <template #actions>
        <AppButton variant="primary" @click="openCreate">+ 新建用户</AppButton>
      </template>
    </PageHeader>

    <div v-if="loading" class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>用户名</th><th>显示名</th><th>状态</th><th>权限</th><th>创建时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="i in 5" :key="i"><td colspan="6"><AppSkeleton variant="text" :width="'100%'" :height=14 /></td></tr>
        </tbody>
      </table>
    </div>
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
              <AppTag :tone="u.status === 'ACTIVE' ? 'success' : 'danger'">
                {{ u.status === "ACTIVE" ? "活跃" : "已禁用" }}
              </AppTag>
            </td>
            <td><code class="auth-str">{{ u.authorities }}</code></td>
            <td class="mono">{{ formatDate(u.createdAt) }}</td>
            <td>
              <AppButton v-if="u.status === 'ACTIVE'" variant="danger" :loading="disablingId === u.id" loading-text="处理中..." @click="handleDisable(u)">禁用</AppButton>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td colspan="6" class="empty">暂无用户</td>
          </tr>
        </tbody>
      </table>
    </div>

    <AppModal :show="showModal" title="新建用户" @close="showModal = false">
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
          <AppButton variant="ghost" type="button" @click="showModal = false">取消</AppButton>
          <AppButton variant="primary" type="submit" :loading="submitting" loading-text="创建中...">创建</AppButton>
        </div>
      </form>
    </AppModal>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { api } from "../../api/client.js";
import { useToast } from "../../composables/useToast.js";
import AppButton from "../../components/ui/AppButton.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppTag from "../../components/ui/AppTag.vue";
import AppModal from "../../components/ui/AppModal.vue";
import PageHeader from "../../components/ui/PageHeader.vue";

const { toast } = useToast();
const users = ref([]);
const loading = ref(true);
const showModal = ref(false);
const submitting = ref(false);
const disablingId = ref(null);
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
  if (submitting.value) return;
  submitting.value = true;
  try {
    await api.post("/api/users", {
      username: createForm.value.username,
      password: createForm.value.password,
      displayName: createForm.value.displayName || undefined,
      authorities: createForm.value.authorities,
    });
    showModal.value = false;
    toast.success("已创建");
    await load();
  } catch (e) { toast.error("创建失败: " + e.message); }
  finally { submitting.value = false; }
}

async function handleDisable(u) {
  if (!confirm(`确定禁用用户 "${u.username}"？`)) return;
  if (disablingId.value) return;
  disablingId.value = u.id;
  try {
    await api.put(`/api/users/${u.id}/disable`);
    toast.success("已禁用");
    await load();
  } catch (e) { toast.error("操作失败: " + e.message); }
  finally { if (disablingId.value === u.id) disablingId.value = null; }
}

function formatDate(s) { return s ? new Date(s).toLocaleString("zh-CN") : "—"; }

onMounted(load);
</script>

<style scoped>
.user-page { max-width: 1080px; }
.table-wrap { overflow-x: auto; }
.data-table thead th { position: sticky; top: 0; z-index: 2; }
.uname { font-weight: 600; }
.mono { font-family: var(--font-mono); font-size: 12px; color: var(--text-secondary); }
.auth-str { font-family: var(--font-mono); font-size: 11px; color: var(--text-muted); }
.empty { text-align: center; padding: 32px; color: var(--text-muted); }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
