<template>
  <div class="page">
    <div class="page-header">
      <h1>用量统计</h1>
    </div>

    <!-- Summary Cards -->
    <div class="stat-row">
      <div class="stat-card accent"><div class="stat-value">{{ summary.totalCalls }}</div><div class="stat-label">总调用次数</div></div>
      <div class="stat-card success"><div class="stat-value">{{ summary.successRate }}%</div><div class="stat-label">成功率</div></div>
      <div class="stat-card"><div class="stat-value">{{ formatTokens(summary.totalTokens) }}</div><div class="stat-label">总 Token 消耗</div></div>
      <div class="stat-card"><div class="stat-value">{{ summary.avgLatency }}ms</div><div class="stat-label">平均延迟</div></div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="table-wrap">
      <table class="data-table">
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
.table-wrap { overflow-x: auto; }
.time-cell { white-space: nowrap; font-size: 12px; font-family: "JetBrains Mono", monospace; }
.mono { font-family: "JetBrains Mono", monospace; font-size: 12px; }
.num { text-align: right; font-family: "JetBrains Mono", monospace; }
.fw { font-weight: 600; }
</style>
