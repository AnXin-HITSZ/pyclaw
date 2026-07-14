<template>
  <div class="chat-page">
    <!-- Header -->
    <div class="chat-header">
      <button class="btn-back" @click="$router.push(`/workspace/claws/${clawId}`)">← Claw 详情</button>
      <h1>{{ claw?.name || "对话" }}</h1>
      <span v-if="claw" class="status-tag" :class="claw.status">{{ claw.status }}</span>
      <select v-if="roles.length" v-model="selectedRoleKey" class="role-select">
        <option v-for="r in roles" :key="r.roleKey" :value="r.roleKey">
          {{ r.displayName }} {{ r.defaultRole ? '(默认)' : '' }}
        </option>
      </select>
    </div>

    <!-- Main area -->
    <div class="chat-body">
      <!-- Session sidebar -->
      <aside class="session-sidebar">
        <button class="btn-new-session" @click="newSession">+ 新建对话</button>
        <div v-if="sessions.length" class="session-list">
          <div v-for="s in sessions" :key="s.sessionId"
               class="session-item" :class="{ active: s.sessionId === activeSessionId }"
               @click="selectSession(s.sessionId)">
            <div class="session-name">{{ s.agentKey || '会话' }}</div>
            <div class="session-meta">{{ s.messageCount }} 条 · {{ formatShort(s.lastActiveAt) }}</div>
          </div>
        </div>
        <p v-else class="no-data">暂无会话</p>
      </aside>

      <!-- Messages -->
      <div class="chat-main">
        <div ref="messagesEl" class="messages-container">
          <div v-if="messages.length === 0 && !sending" class="empty-chat">
            <p>开始与 {{ claw?.name || 'Claw' }} 对话</p>
          </div>
          <div v-for="(m, i) in messages" :key="i" class="message" :class="m.role">
            <div class="message-role">{{ m.role === 'user' ? '你' : roleLabel }}</div>
            <div class="message-content">{{ m.content }}</div>
          </div>
          <div v-if="sending" class="message assistant">
            <div class="message-role">{{ roleLabel }}</div>
            <div class="message-content thinking">思考中...</div>
          </div>
        </div>

        <!-- Error -->
        <div v-if="error" class="chat-error">{{ error }}</div>

        <!-- Input -->
        <div class="chat-input-area">
          <textarea v-model="prompt" :disabled="sending"
                    placeholder="输入消息..." rows="3"
                    @keydown.enter.exact.prevent="sendMessage" />
          <button class="btn-send" :disabled="!prompt.trim() || sending" @click="sendMessage">
            发送
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api/client.js";

const route = useRoute();
const clawId = ref(route.params.id);
const claw = ref(null);
const roles = ref([]);
const sessions = ref([]);
const activeSessionId = ref(null);
const messages = ref([]);
const prompt = ref("");
const sending = ref(false);
const error = ref("");
const selectedRoleKey = ref("");
const messagesEl = ref(null);

const roleLabel = computed(() => {
  const r = roles.value.find(r => r.roleKey === selectedRoleKey.value);
  return r ? r.displayName : "Agent";
});

async function load() {
  try {
    const [c, s] = await Promise.all([
      api.get(`/api/claws/${clawId.value}`),
      api.get(`/api/claws/${clawId.value}/chat/sessions`),
    ]);
    claw.value = c;
    roles.value = (c.roles || []).filter(r => r.enabled);
    sessions.value = s || [];
    // Default to first role's key, or empty
    if (!selectedRoleKey.value && roles.value.length) {
      const def = roles.value.find(r => r.defaultRole) || roles.value[0];
      selectedRoleKey.value = def.roleKey;
    }
  } catch (e) {
    error.value = "加载 Claw 失败: " + e.message;
  }
}

function newSession() {
  activeSessionId.value = null;
  messages.value = [];
  error.value = "";
}

async function selectSession(sid) {
  activeSessionId.value = sid;
  try {
    const data = await api.get(`/api/sessions/${sid}`);
    messages.value = (data.messages || []).map(m => ({
      role: m.role,
      content: m.content,
    }));
  } catch {
    error.value = "加载会话失败";
  }
}

async function sendMessage() {
  const text = prompt.value.trim();
  if (!text || sending.value) return;
  error.value = "";
  sending.value = true;

  // Optimistic user message
  messages.value.push({ role: "user", content: text });
  prompt.value = "";
  await nextTick();
  scrollToBottom();

  try {
    const res = await api.post(`/api/claws/${clawId.value}/chat/runs`, {
      prompt: text,
      roleKey: selectedRoleKey.value || undefined,
      sessionId: activeSessionId.value || undefined,
    });
    activeSessionId.value = res.sessionId;
    messages.value.push({ role: "assistant", content: res.text || "(无回复)" });
    // Refresh session list
    const s = await api.get(`/api/claws/${clawId.value}/chat/sessions`);
    sessions.value = s || [];
  } catch (e) {
    error.value = "发送失败: " + e.message;
    messages.value.push({ role: "assistant", content: "[Error] " + e.message });
  } finally {
    sending.value = false;
    await nextTick();
    scrollToBottom();
  }
}

function scrollToBottom() {
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
  }
}

function formatShort(s) {
  if (!s) return "—";
  return new Date(s).toLocaleString("zh-CN");
}

onMounted(load);
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: calc(100vh - 64px); }
.chat-header { display: flex; align-items: center; gap: 12px; padding-bottom: 16px; border-bottom: 1px solid var(--border-color); flex-shrink: 0; }
.chat-header h1 { font-size: 20px; }
.btn-back { padding: 6px 12px; font-size: 13px; color: var(--text-secondary); background: transparent; border: 1px solid var(--border-color); border-radius: 6px; }
.status-tag { font-size: 11px; padding: 1px 8px; border-radius: 10px; }
.status-tag.active { background: rgba(63,185,80,0.15); color: var(--success); }
.status-tag.inactive { background: rgba(248,81,73,0.15); color: var(--danger); }
.role-select { padding: 6px 10px; background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: 6px; color: var(--text-primary); font-size: 13px; }

.chat-body { display: flex; flex: 1; overflow: hidden; }
.session-sidebar { width: 220px; border-right: 1px solid var(--border-color); padding: 12px; overflow-y: auto; flex-shrink: 0; }
.btn-new-session { width: 100%; padding: 8px; font-size: 13px; color: var(--accent); background: transparent; border: 1px dashed var(--accent); border-radius: 6px; margin-bottom: 12px; }
.session-list { display: flex; flex-direction: column; gap: 4px; }
.session-item { padding: 8px; border-radius: 6px; font-size: 13px; cursor: pointer; }
.session-item:hover { background: var(--bg-tertiary); }
.session-item.active { background: rgba(88,166,255,0.1); }
.session-name { font-weight: 600; }
.session-meta { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

.chat-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.messages-container { flex: 1; overflow-y: auto; padding: 20px; }
.empty-chat { text-align: center; color: var(--text-muted); padding: 48px; }
.message { margin-bottom: 16px; max-width: 80%; }
.message.user { margin-left: auto; }
.message.assistant { margin-right: auto; }
.message-role { font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 4px; }
.message-content { padding: 10px 14px; border-radius: 10px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; }
.message.user .message-content { background: var(--accent); color: #fff; }
.message.assistant .message-content { background: var(--bg-secondary); border: 1px solid var(--border-color); }
.thinking { opacity: 0.6; font-style: italic; }

.chat-error { padding: 8px 20px; font-size: 13px; color: var(--danger); background: rgba(248,81,73,0.1); border-top: 1px solid var(--danger); }

.chat-input-area { display: flex; gap: 8px; padding: 16px 20px; border-top: 1px solid var(--border-color); }
.chat-input-area textarea { flex: 1; padding: 10px 14px; background: var(--bg-primary); border: 1px solid var(--border-color); border-radius: 8px; color: var(--text-primary); font-size: 14px; resize: none; font-family: inherit; }
.chat-input-area textarea:focus { outline: none; border-color: var(--accent); }
.btn-send { padding: 10px 24px; font-size: 14px; font-weight: 600; color: #fff; background: var(--accent); border: none; border-radius: 8px; }
.btn-send:disabled { opacity: 0.5; }
.no-data { color: var(--text-muted); font-size: 12px; padding: 12px 0; }
</style>
