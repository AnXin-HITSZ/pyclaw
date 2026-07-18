<template>
  <div class="usage-page">
    <PageHeader title="用量统计" subtitle="Agent 调用与 Token 消耗概览。" />

    <div class="stat-row">
      <div class="stat-card accent"><div class="stat-value">{{ summary.totalCalls }}</div><div class="stat-label">总调用次数</div></div>
      <div class="stat-card success"><div class="stat-value">{{ summary.successRate }}%</div><div class="stat-label">成功率</div></div>
      <div class="stat-card"><div class="stat-value">{{ formatTokens(summary.totalTokens) }}</div><div class="stat-label">总 Token 消耗</div></div>
      <div class="stat-card"><div class="stat-value">{{ summary.avgLatency }}ms</div><div class="stat-label">平均延迟</div></div>
    </div>

    <div v-if="!loading && records.length" class="chart-panel card">
      <h3 class="section-title">Token 用量分布（最近 {{ chartData.length }} 条）</h3>
      <div class="bar-chart">
        <div v-for="(bar, i) in chartData" :key="i" class="bar-col" :title="bar.label">
          <div class="bar-track">
            <div class="bar-fill" :style="{ height: bar.pct + '%' }"></div>
          </div>
          <span class="bar-axis">{{ bar.short }}</span>
        </div>
      </div>
    </div>

    <div v-if="loading" class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>时间</th><th>用户</th><th>Session</th><th>Provider</th><th>模型</th><th>Prompt</th><th>Completion</th><th>Total</th><th>延迟</th><th>结果</th></tr>
        </thead>
        <tbody>
          <tr v-for="i in 6" :key="i"><td colspan="10"><AppSkeleton variant="text" :width="'100%'" :height=14 /></td></tr>
        </tbody>
      </table>
    </div>
    <div v-else-if="records.length === 0" class="empty-wrap">
      <AppEmpty icon="📊" title="暂无用记录" description="Agent 运行后将在此汇总调用与 Token 消耗。" />
    </div>
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
              <AppTag :tone="r.success ? 'success' : 'danger'">{{ r.success ? "成功" : "失败" }}</AppTag>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { api } from "../../api/client.js";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppTag from "../../components/ui/AppTag.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import PageHeader from "../../components/ui/PageHeader.vue";

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

const chartData = computed(() => {
  const recent = records.value.slice(0, 20).reverse();
  const max = Math.max(1, ...recent.map(r => r.totalTokens || 0));
  return recent.map((r, i) => ({
    pct: Math.round(((r.totalTokens || 0) / max) * 100),
    label: `${formatDate(r.createdAt)} · ${r.totalTokens || 0} tokens`,
    short: (i + 1) % 5 === 0 ? String(i + 1) : "",
  }));
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
.usage-page { max-width: 1200px; }
.table-wrap { overflow-x: auto; }
.data-table thead th { position: sticky; top: 0; z-index: 2; }
.time-cell { white-space: nowrap; font-size: 12px; font-family: var(--font-mono); }
.mono { font-family: var(--font-mono); font-size: 12px; }
.num { text-align: right; font-family: var(--font-mono); }
.fw { font-weight: 600; }
.empty-wrap { margin-top: 24px; }
.chart-panel { margin-bottom: 24px; }
.bar-chart { display: flex; align-items: flex-end; gap: 6px; height: 140px; padding-top: 8px; }
.bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; min-width: 0; }
.bar-track { width: 100%; height: 110px; display: flex; align-items: flex-end; }
.bar-fill { width: 100%; min-height: 2px; border-radius: 4px 4px 0 0; background: linear-gradient(180deg, var(--accent-3), var(--accent)); transition: height 0.4s var(--ease-out); }
.bar-axis { font-size: 10px; color: var(--text-muted); font-family: var(--font-mono); }
</style>
