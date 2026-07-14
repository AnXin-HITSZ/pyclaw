<template>
  <div class="page">
    <div class="page-header">
      <h1>渠道管理</h1>
    </div>

    <div class="tabs">
      <button class="tab" :class="{ active: tab === 'wechat' }" @click="tab = 'wechat'">微信</button>
      <button class="tab" :class="{ active: tab === 'feishu' }" @click="tab = 'feishu'">飞书</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="channel-grid">
      <div v-for="c in filteredChannels" :key="c.id" class="channel-card">
        <div class="channel-header">
          <h3>{{ c.name }}</h3>
          <span class="ch-status" :class="c.enabled ? 'enabled' : 'disabled'">
            {{ c.enabled ? "启用" : "停用" }}
          </span>
        </div>
        <pre class="config-json">{{ formatConfig(c.configJson) }}</pre>
        <div class="ch-actions">
          <button class="btn-sm" @click="openEdit(c)">编辑</button>
          <button class="btn-sm btn-danger" @click="handleDelete(c)">删除</button>
        </div>
      </div>
      <div v-if="filteredChannels.length === 0" class="empty">暂无渠道配置</div>
    </div>

    <button class="btn-primary" style="margin-top:20px" @click="openCreate">+ 新建渠道</button>
    <button v-if="tab==='wechat'" class="btn-secondary" style="margin-top:20px;margin-left:12px" @click="openWechatQuick">微信快捷配置</button>
    <button v-if="tab==='feishu'" class="btn-secondary" style="margin-top:20px;margin-left:12px" @click="openFeishuQuick">飞书快捷配置</button>

    <!-- Create/Edit Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <h2>{{ editing ? "编辑渠道" : "新建渠道" }}</h2>
        <form @submit.prevent="handleSave">
          <div class="form-group">
            <label>渠道类型 *</label>
            <select v-model="form.channelType" required :disabled="!!editing">
              <option value="wechat">微信</option>
              <option value="feishu">飞书</option>
            </select>
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
            <button type="button" class="btn-secondary" @click="showModal = false">取消</button>
            <button type="submit" class="btn-primary">保存</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Quick Config Modal -->
    <div v-if="showQuick" class="modal-overlay" @click.self="showQuick = false">
      <div class="modal">
        <h2>{{ tab === "wechat" ? "微信快捷配置" : "飞书快捷配置" }}</h2>
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
            <button type="button" class="btn-secondary" @click="showQuick = false">取消</button>
            <button type="submit" class="btn-primary">创建</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { api } from "../../api/client.js";

const channels = ref([]);
const loading = ref(true);
const tab = ref("wechat");
const showModal = ref(false);
const showQuick = ref(false);
const editing = ref(null);
const form = ref({});
const quickForm = ref({ name: "", appId: "", callbackPath: "", secretRef: "" });

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
  try {
    let configObj;
    try { configObj = JSON.parse(form.value.configJsonStr); } catch { alert("Config JSON 格式错误"); return; }
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
    await load();
  } catch (e) { alert("保存失败: " + e.message); }
}

async function handleQuick() {
  try {
    const url = tab.value === "wechat" ? "/api/channels/wechat/config" : "/api/channels/feishu/config";
    await api.post(url, {
      name: quickForm.value.name || undefined,
      appId: quickForm.value.appId,
      callbackPath: quickForm.value.callbackPath || undefined,
      secretRef: quickForm.value.secretRef || undefined,
    });
    showQuick.value = false;
    await load();
  } catch (e) { alert("创建失败: " + e.message); }
}

async function handleDelete(c) {
  if (!confirm(`确定删除渠道 "${c.name}"？`)) return;
  try { await api.delete(`/api/channels/${c.id}`); await load(); }
  catch (e) { alert("删除失败: " + e.message); }
}

function formatConfig(json) {
  try { return JSON.stringify(JSON.parse(json), null, 2); } catch { return json; }
}

onMounted(load);
</script>

<style scoped>
.page { max-width: 900px; }
.page-header { margin-bottom: 16px; }
.page-header h1 { font-size: 24px; }
.tabs { display: flex; gap: 8px; margin-bottom: 20px; }
.tab { padding: 6px 16px; font-size: 13px; color: var(--text-secondary); background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 6px; }
.tab.active { background: rgba(88,166,255,0.1); color: var(--accent); border-color: var(--accent); }
.channel-grid { display: grid; gap: 16px; }
.channel-card { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 20px; }
.channel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.channel-header h3 { font-size: 16px; }
.ch-status { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.ch-status.enabled { background: rgba(63,185,80,0.15); color: var(--success); }
.ch-status.disabled { background: rgba(110,118,129,0.15); color: var(--text-muted); }
.config-json { font-size: 12px; color: var(--text-secondary); background: var(--bg-primary); padding: 12px; border-radius: 6px; overflow-x: auto; margin-bottom: 12px; }
.ch-actions { display: flex; gap: 8px; }
.btn-sm { padding: 4px 12px; font-size: 12px; border-radius: 4px; border: 1px solid var(--border-color); background: transparent; color: var(--text-secondary); }
.btn-danger { color: var(--danger); border-color: var(--danger); }
.btn-primary { padding: 8px 20px; font-size: 14px; font-weight: 600; color: #fff; background: var(--accent); border: none; border-radius: 6px; }
.btn-secondary { padding: 8px 20px; font-size: 14px; color: var(--text-secondary); background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: 6px; }
.loading, .empty { text-align: center; padding: 48px; color: var(--text-secondary); }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 12px; padding: 32px; width: 500px; max-width: 90vw; max-height: 90vh; overflow-y: auto; }
.modal h2 { margin-bottom: 20px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 13px; color: var(--text-secondary); }
.form-group input, .form-group textarea, .form-group select {
  width: 100%; padding: 8px 12px; background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: 6px; color: var(--text-primary); font-size: 14px;
}
.form-group input:focus, .form-group textarea:focus, .form-group select:focus { outline: none; border-color: var(--accent); }
.checkbox-label { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-primary); }
.checkbox-label input { width: auto; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
