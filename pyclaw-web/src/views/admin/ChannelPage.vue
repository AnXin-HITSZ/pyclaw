<template>
  <div class="channel-page">
    <PageHeader title="渠道管理" subtitle="管理微信 / 飞书接入渠道及其凭据引用。" />

    <div class="tabs">
      <button class="tab" :class="{ active: tab === 'wechat' }" @click="tab = 'wechat'">微信</button>
      <button class="tab" :class="{ active: tab === 'feishu' }" @click="tab = 'feishu'">飞书</button>
    </div>

    <div v-if="loading" class="channel-grid">
      <div v-for="i in 3" :key="i" class="card channel-card skeleton-card">
        <AppSkeleton variant="text" :width="'50%'" :height=16 />
        <AppSkeleton variant="text" :width="'90%'" :height=12 />
        <AppSkeleton variant="text" :width="'70%'" :height=12 />
      </div>
    </div>
    <div v-else-if="filteredChannels.length === 0" class="empty-wrap">
      <AppEmpty icon="📡" title="暂无渠道配置" description="新建渠道以接入微信或飞书。" />
    </div>
    <div v-else class="channel-grid">
      <article v-for="c in filteredChannels" :key="c.id" class="card channel-card">
        <div class="channel-header">
          <h3>{{ c.name }}</h3>
          <AppTag :tone="c.enabled ? 'success' : 'neutral'">{{ c.enabled ? "启用" : "停用" }}</AppTag>
        </div>
        <pre class="config-json">{{ formatConfig(c.configJson) }}</pre>
        <div class="ch-actions">
          <AppButton variant="ghost" @click="openEdit(c)">编辑</AppButton>
          <AppButton variant="danger" :loading="deletingId === c.id" loading-text="删除中..." @click="handleDelete(c)">删除</AppButton>
        </div>
      </article>
    </div>

    <div class="page-actions">
      <AppButton variant="primary" @click="openCreate">+ 新建渠道</AppButton>
      <AppButton v-if="tab==='wechat'" variant="ghost" @click="openWechatQuick">微信快捷配置</AppButton>
      <AppButton v-if="tab==='feishu'" variant="ghost" @click="openFeishuQuick">飞书快捷配置</AppButton>
    </div>

    <AppModal :show="showModal" :title="editing ? '编辑渠道' : '新建渠道'" @close="showModal = false">
      <form @submit.prevent="handleSave">
        <div class="form-group">
          <label>渠道类型 *</label>
          <AppSelect
            v-model="form.channelType"
            :options="[{value:'wechat',label:'微信'},{value:'feishu',label:'飞书'}]"
            :disabled="!!editing"
            required
          />
        </div>
        <div class="form-group">
          <label>名称 *</label>
          <input v-model="form.name" required />
        </div>
        <div class="form-group">
          <label>Config JSON</label>
          <textarea v-model="form.configJsonStr" rows="6" placeholder='{"appId": "wx..."}' />
        </div>
        <div class="form-group">
          <label>Secret Ref</label>
          <input v-model="form.secretRef" placeholder="K8s Secret 名称" />
        </div>
        <div class="form-group">
          <label class="checkbox-label">
            <input type="checkbox" v-model="form.enabled" /> 启用
          </label>
        </div>
        <div class="modal-actions">
          <AppButton variant="ghost" type="button" @click="showModal = false">取消</AppButton>
          <AppButton variant="primary" type="submit" :loading="submitting" loading-text="保存中...">保存</AppButton>
        </div>
      </form>
    </AppModal>

    <AppModal :show="showQuick" :title="tab === 'wechat' ? '微信快捷配置' : '飞书快捷配置'" @close="showQuick = false">
      <form @submit.prevent="handleQuick">
        <div class="form-group">
          <label>名称</label>
          <input v-model="quickForm.name" :placeholder="tab === 'wechat' ? 'wechat' : 'feishu'" />
        </div>
        <div class="form-group">
          <label>App ID</label>
          <input v-model="quickForm.appId" required />
        </div>
        <div class="form-group">
          <label>Callback Path</label>
          <input v-model="quickForm.callbackPath" :placeholder="tab === 'wechat' ? '/api/webhooks/wechat' : '/api/webhooks/feishu'" />
        </div>
        <div class="form-group">
          <label>Secret Ref</label>
          <input v-model="quickForm.secretRef" />
        </div>
        <div class="modal-actions">
          <AppButton variant="ghost" type="button" @click="showQuick = false">取消</AppButton>
          <AppButton variant="primary" type="submit" :loading="submitting" loading-text="创建中...">创建</AppButton>
        </div>
      </form>
    </AppModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { api } from "../../api/client.js";
import { useToast } from "../../composables/useToast.js";
import AppButton from "../../components/ui/AppButton.vue";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppTag from "../../components/ui/AppTag.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import AppModal from "../../components/ui/AppModal.vue";
import AppSelect from "../../components/ui/AppSelect.vue";
import PageHeader from "../../components/ui/PageHeader.vue";

const { toast } = useToast();
const channels = ref([]);
const loading = ref(true);
const tab = ref("wechat");
const showModal = ref(false);
const showQuick = ref(false);
const editing = ref(null);
const form = ref({});
const quickForm = ref({ name: "", appId: "", callbackPath: "", secretRef: "" });
const submitting = ref(false);
const deletingId = ref(null);

const filteredChannels = computed(() => channels.value.filter(c => c.channelType === tab.value));

async function load() {
  loading.value = true;
  try { channels.value = await api.get("/api/channels"); } catch {}
  finally { loading.value = false; }
}

function openCreate() {
  editing.value = null;
  form.value = { channelType: tab.value, name: "", configJsonStr: "{}", secretRef: "", enabled: true };
  showModal.value = true;
}
function openEdit(c) {
  editing.value = c;
  form.value = {
    channelType: c.channelType, name: c.name,
    configJsonStr: (() => { try { return JSON.stringify(JSON.parse(c.configJson), null, 2); } catch { return c.configJson; } })(),
    secretRef: c.secretRef || "", enabled: c.enabled,
  };
  showModal.value = true;
}
function openWechatQuick() {
  quickForm.value = { name: "", appId: "", callbackPath: "/api/webhooks/wechat", secretRef: "" };
  showQuick.value = true;
}
function openFeishuQuick() {
  quickForm.value = { name: "", appId: "", callbackPath: "/api/webhooks/feishu", secretRef: "" };
  showQuick.value = true;
}

async function handleSave() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    let configObj;
    try { configObj = JSON.parse(form.value.configJsonStr); } catch { toast.error("Config JSON 格式错误"); submitting.value = false; return; }
    const body = {
      channelType: form.value.channelType, name: form.value.name,
      config: configObj, secretRef: form.value.secretRef || undefined, enabled: form.value.enabled,
    };
    if (editing.value) {
      await api.put(`/api/channels/${editing.value.id}`, body);
    } else {
      await api.post("/api/channels", body);
    }
    showModal.value = false;
    toast.success("已保存");
    await load();
  } catch (e) { toast.error("保存失败: " + e.message); }
  finally { submitting.value = false; }
}

async function handleQuick() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    const url = tab.value === "wechat" ? "/api/channels/wechat/config" : "/api/channels/feishu/config";
    await api.post(url, {
      name: quickForm.value.name || undefined,
      appId: quickForm.value.appId,
      callbackPath: quickForm.value.callbackPath || undefined,
      secretRef: quickForm.value.secretRef || undefined,
    });
    showQuick.value = false;
    toast.success("已创建");
    await load();
  } catch (e) { toast.error("创建失败: " + e.message); }
  finally { submitting.value = false; }
}

async function handleDelete(c) {
  if (!confirm(`确定删除渠道 "${c.name}"？`)) return;
  if (deletingId.value) return;
  deletingId.value = c.id;
  try { await api.delete(`/api/channels/${c.id}`); toast.success("已删除"); await load(); }
  catch (e) { toast.error("删除失败: " + e.message); }
  finally { if (deletingId.value === c.id) deletingId.value = null; }
}

function formatConfig(json) {
  try { return JSON.stringify(JSON.parse(json), null, 2); } catch { return json; }
}

onMounted(load);
</script>

<style scoped>
.channel-page { max-width: 960px; }
.tabs { display: flex; gap: 8px; margin-bottom: 20px; }
.tab { padding: 6px 16px; font-size: 13px; color: var(--text-secondary); background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 999px; cursor: pointer; transition: all 0.15s var(--ease-out); }
.tab.active { background: rgba(240,163,58,0.1); color: var(--accent); border-color: var(--accent); }
.channel-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.channel-card { display: flex; flex-direction: column; gap: 12px; }
.skeleton-card { gap: 12px; padding: 22px; }
.channel-header { display: flex; justify-content: space-between; align-items: center; }
.channel-header h3 { font-size: 16px; margin: 0; }
.config-json { font-family: var(--font-mono); font-size: 12px; color: var(--text-secondary); background: var(--bg-primary); padding: 12px; border-radius: 6px; overflow-x: auto; margin: 0; }
.ch-actions { display: flex; gap: 8px; }
.page-actions { display: flex; gap: 10px; margin-top: 20px; flex-wrap: wrap; }
.empty-wrap { margin-top: 24px; }
.checkbox-label { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-primary); }
.checkbox-label input { width: auto; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
