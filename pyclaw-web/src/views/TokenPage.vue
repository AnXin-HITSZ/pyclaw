<template>
  <div class="token-page">
    <PageHeader title="API Token" subtitle="程序化调用 Agent API 的访问凭据，创建后仅展示一次。">
      <template #actions>
        <AppButton variant="primary" @click="openCreate">+ 新建 Token</AppButton>
      </template>
    </PageHeader>

    <div v-if="loading" class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th><th>Scopes</th><th>创建时间</th><th>过期时间</th><th>最后使用</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in 4" :key="i">
            <td colspan="7"><AppSkeleton variant="text" :width="'100%'" :height="14" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else-if="tokens.length === 0" class="empty-wrap">
      <AppEmpty icon="🔑" title="还没有 API Token" description="创建 API Token 后可用于程序化调用 Agent API。">
        <AppButton variant="primary" @click="openCreate">+ 新建 Token</AppButton>
      </AppEmpty>
    </div>
    <div v-else class="table-wrap">
      <table class="data-table">
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
            <td><code class="scope-tag">{{ t.scopes }}</code></td>
            <td class="mono">{{ formatDate(t.createdAt) }}</td>
            <td class="mono">{{ t.expiresAt ? formatDate(t.expiresAt) : "永不过期" }}</td>
            <td class="mono">{{ t.lastUsedAt ? formatDate(t.lastUsedAt) : "从未使用" }}</td>
            <td>
              <AppTag :tone="t.revokedAt ? 'danger' : 'success'" :pulse="!t.revokedAt">
                {{ t.revokedAt ? "已撤销" : "有效" }}
              </AppTag>
            </td>
            <td>
              <AppButton v-if="!t.revokedAt" variant="danger" :loading="revokingId === t.id" loading-text="撤销中..." @click="handleRevoke(t)">撤销</AppButton>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <AppModal :show="showCreate" title="新建 API Token" @close="showCreate = false">
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
          <AppButton variant="ghost" type="button" @click="showCreate = false">取消</AppButton>
          <AppButton variant="primary" type="submit" :loading="submitting" loading-text="创建中...">创建</AppButton>
        </div>
      </form>
    </AppModal>

    <AppModal :show="!!newToken" title="Token 已创建" @close="newToken = null">
      <p class="warning-text">此 Token 只展示一次，请立即复制保存：</p>
      <div class="token-reveal">
        <code>{{ newToken }}</code>
        <button class="copy-btn" @click="copyToken">复制</button>
      </div>
      <template #actions>
        <AppButton variant="primary" @click="newToken = null">我已保存，关闭</AppButton>
      </template>
    </AppModal>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { api } from "../api/client.js";
import { useToast } from "../composables/useToast.js";
import AppButton from "../components/ui/AppButton.vue";
import AppSkeleton from "../components/ui/AppSkeleton.vue";
import AppTag from "../components/ui/AppTag.vue";
import AppEmpty from "../components/ui/AppEmpty.vue";
import AppModal from "../components/ui/AppModal.vue";
import PageHeader from "../components/ui/PageHeader.vue";

const { toast } = useToast();
const tokens = ref([]);
const loading = ref(true);
const showCreate = ref(false);
const newToken = ref(null);
const submitting = ref(false);
const revokingId = ref(null);
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
  if (submitting.value) return;
  submitting.value = true;
  try {
    const body = { name: createForm.value.name, scopes: createForm.value.scopes };
    if (createForm.value.expiresAt) body.expiresAt = new Date(createForm.value.expiresAt).toISOString();
    const res = await api.post("/api/tokens", body);
    newToken.value = res.token;
    showCreate.value = false;
    createForm.value = { name: "", expiresAt: "", scopes: ["agent:run"] };
    toast.success("已创建");
    await load();
  } catch (e) {
    toast.error("创建失败: " + e.message);
  } finally {
    submitting.value = false;
  }
}

async function handleRevoke(t) {
  if (!confirm(`确定撤销 Token "${t.name}"？`)) return;
  if (revokingId.value) return;
  revokingId.value = t.id;
  try {
    await api.delete(`/api/tokens/${t.id}`);
    toast.success("已撤销");
    await load();
  } catch (e) {
    toast.error("撤销失败: " + e.message);
  } finally {
    if (revokingId.value === t.id) revokingId.value = null;
  }
}

async function copyToken() {
  try {
    await navigator.clipboard.writeText(newToken.value);
    toast.success("已复制");
  } catch (e) {
    toast.error("复制失败: " + e.message);
  }
}

function formatDate(s) {
  if (!s) return "—";
  return new Date(s).toLocaleString("zh-CN");
}

onMounted(load);
</script>

<style scoped>
.token-page { max-width: 1080px; }
.table-wrap { overflow-x: auto; }
.token-name { font-weight: 600; }
.mono { font-family: var(--font-mono); font-size: 12px; color: var(--text-secondary); }
.scope-tag { font-family: var(--font-mono); font-size: 11px; color: var(--accent); }
.empty-wrap { margin-top: 24px; }
.scope-select { display: flex; flex-direction: column; gap: 8px; }
.checkbox-label { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-primary); }
.checkbox-label input { width: auto; }
.warning-text { color: var(--warning); font-size: 14px; margin-bottom: 12px; }
.token-reveal { position: relative; background: var(--bg-primary); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 16px; margin-bottom: 20px; word-break: break-all; }
.token-reveal code { color: var(--success); font-size: 13px; font-family: var(--font-mono); }
.copy-btn { position: absolute; top: 10px; right: 10px; font-size: 11px; padding: 4px 10px; color: var(--text-muted); background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 6px; cursor: pointer; transition: all 0.15s; }
.copy-btn:hover { color: var(--accent); border-color: var(--accent); }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
