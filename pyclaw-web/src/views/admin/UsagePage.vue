<template>
  <div class="page">
    <div class="page-header">
      <h1>用量统计</h1>
    </div>

    <!-- Summary Cards -->
    <div class="summary-grid">
      <div class="summary-card">
        <span class="summary-value">{{ summary.totalCalls }}</span>
        <span class="summary-label">总调用次数</span>
      </div>
      <div class="summary-card">
        <span class="summary-value">{{ summary.successRate }}%</span>
        <span class="summary-label">成功率</span>
      </div>
      <div class="summary-card">
        <span class="summary-value">{{ formatTokens(summary.totalTokens) }}</span>
        <span class="summary-label">总 Token 消耗</span>
      </div>
      <div class="summary-card">
        <span class="summary-value">{{ summary.avgLatency }}ms</span>
        <span class="summary-label">平均延迟</span>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="table-wrap">
      <table class="usage-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>用户</th>
            <th>Session</th>
            <th>Provider</th>
            <th>模型</th>
            <th>Prompt Tokens</th>
            <th>Completion</th>
            <th>Total</th>
            <th>延迟</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in records" :key="r.id">
            <td class="time-cell">{{ formatDate(r.createdAt) }}</td>
            <td class="mono">{{ truncate(r.userId, 8) }}</td>
            <td class="mono">{{ truncate(r.sessionId, 8) }}</td>
            <td>{{ r.provider }}</td>
            <td>{{ r.model }}</td>
            <td class="num">{{ r.promptTokens }}</td>
            <td class="num">{{ r.completionTokens }}</td>
            <td class="num fw">{{ r.totalTokens }}</td>
            <td class="num">{{ r.latencyMs }}ms</td>
            <td>
              <span class="status-tag" :class="r.success ? 'success' : 'fail'">{{ r.success ? "成功" : "失败" }}</span>
            </td>
          </tr>
          <tr v-if="records.length === 0">
            <td colspan="10" class="empty">暂无用记录</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { api } from "../../api/client.js";

const records = ref([]);
const loading = ref(true);

const summary = computed(() => {
  const total = records.value.length;
  const success = records.value.filter(r => r.success).length;
  const totalTokens = records.value.reduce((s, r) => s + (r.totalTokens || 0), 0);
  const totalLatency = records.value.reduce((s, r) => s + (r.latencyMs || 0), 0);
  return {
    totalCalls: total,
    successRate: total ? Math.round((success / total) * 100) : 0,
    totalTokens,
    avgLatency: total ? Math.round(totalLatency / total) : 0,
  };
});

async function load() {
  try { records.value = await api.get("/api/usage-records"); } catch {}
  finally { loading.value = false; }
}

function formatTokens(n) {
  if (!n) return "0";
  if (n >= 1000000) return (n / 1000000).toFixed(1) + "M";
  if (n >= 1000) return (n / 1000).toFixed(1) + "K";
  return String(n);
}

function formatDate(s) { return s ? new Date(s).toLocaleString("zh-CN") : "—"; }
function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + "..." : s || "—"; }

onMounted(load);
</script>

<style scoped>
.page { max-width: 1200px; }
.page-header { margin-bottom: 24px; }
.page-header h1 { font-size: 24px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.summary-card { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 20px; text-align: center; }
.summary-value { display: block; font-size: 28px; font-weight: 700; color: var(--accent); }
.summary-label { display: block; font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.table-wrap { overflow-x: auto; }
.usage-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.usage-table th { text-align: left; padding: 10px 12px; background: var(--bg-secondary); color: var(--text-secondary); font-weight: 600; border-bottom: 1px solid var(--border-color); white-space: nowrap; }
.usage-table td { padding: 10px 12px; border-bottom: 1px solid var(--border-color); }
.time-cell { white-space: nowrap; font-size: 12px; font-family: monospace; }
.mono { font-family: monospace; font-size: 12px; }
.num { text-align: right; font-family: monospace; }
.fw { font-weight: 600; }
.status-tag { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.status-tag.success { background: rgba(63,185,80,0.15); color: var(--success); }
.status-tag.fail { background: rgba(248,81,73,0.15); color: var(--danger); }
.empty { text-align: center; color: var(--text-secondary); }
.loading { text-align: center; padding: 48px; color: var(--text-secondary); }
</style>
