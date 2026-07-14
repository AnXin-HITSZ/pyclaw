<template>
  <div class="page">
    <div class="page-header">
      <h1>Agent 对话</h1>
    </div>

    <div class="playground-layout">
      <!-- Config Panel -->
      <div class="config-panel">
        <div class="form-group">
          <label>Agent</label>
          <select v-model="config.agentKey">
            <option value="">使用默认 Agent</option>
            <option v-for="a in agents" :key="a.agentKey" :value="a.agentKey">
              {{ a.name }} ({{ a.agentKey }})
            </option>
          </select>
        </div>
        <div class="form-group">
          <label>Provider</label>
          <select v-model="config.provider">
            <option value="">默认</option>
            <option v-for="p in providers" :key="p.name" :value="p.name">{{ p.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>Model</label>
          <input v-model="config.model" placeholder="使用默认模型" />
        </div>
        <div class="form-group">
          <label>Tool Profile</label>
          <select v-model="config.toolProfile">
            <option value="minimal">minimal</option>
            <option value="readonly">readonly</option>
            <option value="coding">coding</option>
            <option value="messaging">messaging</option>
            <option value="full">full</option>
          </select>
        </div>
        <div class="form-group">
          <label>Session ID（留空自动生成）</label>
          <input v-model="config.sessionId" placeholder="连续对话的标识" />
        </div>
        <div v-if="lastResult" class="result-meta">
          <span>Session: {{ lastResult.sessionId }}</span>
          <span>耗时: {{ lastResult.latencyMs }}ms</span>
        </div>
      </div>

      <!-- Chat Area -->
      <div class="chat-area">
        <div class="chat-messages" ref="messagesEl">
          <div v-if="messages.length === 0" class="chat-empty">
            选择一个 Agent，开始对话
          </div>
          <div v-for="(msg, i) in messages" :key="i" class="chat-message" :class="msg.role">
            <div class="message-role">{{ msg.role === "user" ? "你" : "Agent" }}</div>
            <div class="message-content">{{ msg.content }}</div>
          </div>
          <div v-if="sending" class="chat-message assistant">
            <div class="message-role">Agent</div>
            <div class="message-content typing">思考中...</div>
          </div>
        </div>
        <div class="chat-input">
          <textarea
            v-model="input"
            placeholder="输入消息，按 Enter 发送（Shift+Enter 换行）"
            rows="3"
            @keydown.enter.exact.prevent="handleSend"
          />
          <button class="btn-primary" :disabled="sending || !input.trim()" @click="handleSend">
            {{ sending ? "发送中..." : "发送" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from "vue";
import { api } from "../api/client.js";

const agents = ref([]);
const providers = ref([]);
const messages = ref([]);
const input = ref("");
const sending = ref(false);
const lastResult = ref(null);
const messagesEl = ref(null);

const config = ref({
  agentKey: "", provider: "", model: "", toolProfile: "messaging", sessionId: "",
});

async function handleSend() {
  if (!input.value.trim() || sending.value) return;
  const prompt = input.value.trim();
  input.value = "";
  messages.value.push({ role: "user", content: prompt });
  sending.value = true;
  await nextTick();
  scrollBottom();

  try {
    const body = { prompt };
    if (config.value.provider) body.provider = config.value.provider;
    if (config.value.model) body.model = config.value.model;
    if (config.value.toolProfile) body.toolProfile = config.value.toolProfile;
    if (config.value.sessionId) body.sessionId = config.value.sessionId;

    const res = await api.post("/api/agent/run", body);
    messages.value.push({ role: "assistant", content: res.text || "(empty response)" });
    lastResult.value = res;
    if (!config.value.sessionId) config.value.sessionId = res.sessionId;
  } catch (e) {
    messages.value.push({ role: "assistant", content: `[Error] ${e.message}` });
  } finally {
    sending.value = false;
    await nextTick();
    scrollBottom();
  }
}

function scrollBottom() {
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
  }
}

onMounted(async () => {
  try {
    const [a, p] = await Promise.all([
      api.get("/api/agents"),
      api.get("/api/providers/options").catch(() => []),
    ]);
    agents.value = a;
    providers.value = p;
  } catch {}
});
</script>

<style scoped>
.page { max-width: 1200px; }
.page-header { margin-bottom: 20px; }
.page-header h1 { font-size: 24px; }
.playground-layout { display: grid; grid-template-columns: 260px 1fr; gap: 20px; height: calc(100vh - 160px); }

.config-panel {
  background: var(--bg-secondary); border: 1px solid var(--border-color);
  border-radius: 10px; padding: 20px; overflow-y: auto;
}
.config-panel .form-group { margin-bottom: 14px; }
.config-panel label { display: block; margin-bottom: 4px; font-size: 12px; color: var(--text-secondary); }
.config-panel input, .config-panel select {
  width: 100%; padding: 6px 10px; background: var(--bg-primary); border: 1px solid var(--border-color);
  border-radius: 6px; color: var(--text-primary); font-size: 13px;
}
.config-panel input:focus, .config-panel select:focus { outline: none; border-color: var(--accent); }
.result-meta { margin-top: 12px; display: flex; flex-direction: column; gap: 4px; font-size: 11px; color: var(--text-muted); }

.chat-area { display: flex; flex-direction: column; }
.chat-messages {
  flex: 1; overflow-y: auto; background: var(--bg-secondary); border: 1px solid var(--border-color);
  border-radius: 10px; padding: 20px; margin-bottom: 12px;
}
.chat-empty { text-align: center; color: var(--text-muted); padding: 48px; }
.chat-message { margin-bottom: 16px; }
.message-role { font-size: 12px; font-weight: 600; margin-bottom: 4px; }
.chat-message.user .message-role { color: var(--accent); }
.chat-message.assistant .message-role { color: var(--success); }
.message-content { font-size: 14px; line-height: 1.6; white-space: pre-wrap; }
.typing { color: var(--text-muted); font-style: italic; }

.chat-input { display: flex; gap: 12px; align-items: flex-end; }
.chat-input textarea {
  flex: 1; padding: 10px 14px; background: var(--bg-secondary); border: 1px solid var(--border-color);
  border-radius: 8px; color: var(--text-primary); font-size: 14px; resize: none;
}
.chat-input textarea:focus { outline: none; border-color: var(--accent); }
.btn-primary { padding: 10px 24px; font-size: 14px; font-weight: 600; color: #fff; background: var(--accent); border: none; border-radius: 6px; white-space: nowrap; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
