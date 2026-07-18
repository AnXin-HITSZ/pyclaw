<template>
  <div class="secret-page">
    <PageHeader title="Secret 管理" subtitle="集中托管 Provider / 飞书 / 自定义凭据，按用户或 Claw 维度隔离。">
      <template #actions>
        <AppButton variant="primary" @click="openCreate">+ 新建 Secret</AppButton>
      </template>
    </PageHeader>

    <div v-if="loading" class="secret-grid">
      <div v-for="i in 4" :key="i" class="card secret-card skeleton-card">
        <AppSkeleton variant="text" :width="'50%'" :height="18" />
        <AppSkeleton variant="text" :width="'30%'" :height="12" />
        <AppSkeleton variant="text" :width="'80%'" :height=12 />
        <AppSkeleton variant="text" :width="'60%'" :height=12 />
      </div>
    </div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>
    <div v-else-if="!secrets.length" class="empty-wrap">
      <AppEmpty icon="🔐" title="还没有 Secret" description="新建 Secret 后可在 Provider / 渠道中通过 secretRef 引用。">
        <AppButton variant="primary" @click="openCreate">+ 创建第一个 Secret</AppButton>
      </AppEmpty>
    </div>
    <div v-else class="secret-grid">
      <article v-for="(s, index) in secrets" :key="s.id" class="card secret-card" :style="{ transitionDelay: `${index * 40}ms` }">
        <div class="secret-header">
          <h3>{{ s.name }}</h3>
          <div class="secret-tags">
            <AppTag tone="info">{{ s.type }}</AppTag>
            <AppTag tone="neutral">{{ s.scope === 'claw' ? 'Claw' : '用户' }}</AppTag>
            <AppTag v-if="!s.enabled" tone="danger">已禁用</AppTag>
          </div>
        </div>
        <div class="secret-body">
          <div v-if="s.kubernetesSecretName" class="ref-line">
            <span class="ref-label">K8s</span>
            <code class="ref-value">{{ s.kubernetesSecretName }}</code>
          </div>
          <div v-if="s.clawId" class="ref-line">
            <span class="ref-label">Claw</span>
            <code class="ref-value">{{ s.clawId }}</code>
          </div>
          <div v-if="s.maskedValues" class="masked-values">
            <div v-for="(v, k) in s.maskedValues" :key="k" class="kv-pair">
              <span class="kv-key">{{ k }}</span>
              <code class="kv-value">{{ v }}</code>
              <button class="copy-btn" title="复制" @click="copyValue(v)">复制</button>
            </div>
          </div>
        </div>
        <div class="secret-actions">
          <AppButton variant="ghost" :loading="syncingId === s.id" loading-text="同步中..." @click="syncSecret(s.id)">同步到 K8s</AppButton>
          <AppButton variant="danger" :loading="deletingId === s.id" loading-text="删除中..." @click="confirmDelete(s.id)">删除</AppButton>
        </div>
      </article>
    </div>

    <AppModal :show="showCreate" title="新建 Secret" @close="showCreate = false">
      <form @submit.prevent="handleCreate">
        <div class="form-group">
          <label>名称 *</label>
          <input v-model="createForm.name" required />
        </div>
        <div class="form-group">
          <label>类型</label>
          <select v-model="createForm.type">
            <option value="provider">Provider</option>
            <option value="feishu">飞书</option>
            <option value="custom">自定义</option>
          </select>
        </div>
        <div class="form-group">
          <label>范围</label>
          <select v-model="createForm.scope">
            <option value="user">用户级</option>
            <option value="claw">Claw 级</option>
          </select>
        </div>
        <div v-if="createForm.scope === 'claw'" class="form-group">
          <label>Claw *</label>
          <select v-model="createForm.clawId" required>
            <option value="">选择 Claw</option>
            <option v-for="claw in claws" :key="claw.id" :value="claw.id">{{ claw.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>键值对 (每行: KEY=VALUE)</label>
          <textarea v-model="createForm.valuesText" rows="5" placeholder="DEEPSEEK_API_KEY=sk-..." />
        </div>
        <div class="modal-actions">
          <AppButton variant="ghost" type="button" @click="showCreate = false">取消</AppButton>
          <AppButton variant="primary" type="submit" :loading="submitting" loading-text="创建中...">创建</AppButton>
        </div>
      </form>
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
const secrets = ref([]);
const claws = ref([]);
const loading = ref(true);
const error = ref("");
const showCreate = ref(false);
const submitting = ref(false);
const syncingId = ref(null);
const deletingId = ref(null);
const createForm = ref({ name: "", type: "provider", scope: "user", clawId: "", valuesText: "" });

async function load() {
  loading.value = true;
  try {
    const [s, c] = await Promise.all([
      api.get("/api/secrets"),
      api.get("/api/claws"),
    ]);
    secrets.value = s || [];
    claws.value = c || [];
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  createForm.value = { name: "", type: "provider", scope: "user", clawId: "", valuesText: "" };
  showCreate.value = true;
}

async function handleCreate() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    const values = {};
    const lines = (createForm.value.valuesText || "").split("\n").filter(l => l.trim());
    for (const line of lines) {
      const idx = line.indexOf("=");
      if (idx > 0) {
        values[line.substring(0, idx).trim()] = line.substring(idx + 1).trim();
      }
    }
    await api.post("/api/secrets", {
      name: createForm.value.name,
      type: createForm.value.type,
      scope: createForm.value.scope,
      clawId: createForm.value.clawId || undefined,
      values,
    });
    showCreate.value = false;
    toast.success("已创建");
    await load();
  } catch (e) {
    toast.error("创建失败: " + e.message);
  } finally {
    submitting.value = false;
  }
}

async function syncSecret(id) {
  if (syncingId.value) return;
  syncingId.value = id;
  try {
    await api.post(`/api/secrets/${id}/sync`);
    toast.success("同步成功");
    await load();
  } catch (e) {
    toast.error("同步失败: " + e.message);
  } finally {
    if (syncingId.value === id) syncingId.value = null;
  }
}

async function confirmDelete(id) {
  if (!confirm("确定删除此 Secret?")) return;
  if (deletingId.value) return;
  deletingId.value = id;
  try {
    await api.delete(`/api/secrets/${id}`);
    toast.success("已删除");
    await load();
  } catch (e) {
    toast.error("删除失败: " + e.message);
  } finally {
    if (deletingId.value === id) deletingId.value = null;
  }
}

async function copyValue(v) {
  try {
    await navigator.clipboard.writeText(v);
    toast.success("已复制");
  } catch (e) {
    toast.error("复制失败: " + e.message);
  }
}

onMounted(load);
</script>

<style scoped>
.secret-page { max-width: 960px; }
.secret-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 20px; }
.secret-card { display: flex; flex-direction: column; gap: 10px; animation: card-in 0.4s var(--ease-out) both; }
@keyframes card-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.skeleton-card { gap: 12px; padding: 22px; }
.secret-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.secret-header h3 { font-size: 16px; margin: 0; }
.secret-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.secret-body { font-size: 13px; display: flex; flex-direction: column; gap: 6px; }
.ref-line { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.ref-label { color: var(--text-muted); font-size: 11px; text-transform: uppercase; letter-spacing: 0.4px; }
.ref-value { font-family: var(--font-mono); color: var(--text-secondary); font-size: 12px; }
.masked-values { display: flex; flex-direction: column; gap: 4px; margin-top: 4px; }
.kv-pair { display: flex; align-items: center; gap: 8px; }
.kv-key { color: var(--text-secondary); font-size: 12px; min-width: 0; }
.kv-value { font-family: var(--font-mono); font-size: 12px; color: var(--text-muted); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.copy-btn { font-size: 11px; padding: 2px 8px; color: var(--text-muted); background: transparent; border: 1px solid var(--border); border-radius: 6px; cursor: pointer; transition: all 0.15s; }
.copy-btn:hover { color: var(--accent); border-color: var(--accent); }
.secret-actions { display: flex; gap: 8px; margin-top: 8px; }
.empty-wrap { margin-top: 24px; }
.error-msg { text-align: center; padding: 48px; color: var(--danger); }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
