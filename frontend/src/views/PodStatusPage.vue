<template>
  <div class="pod-page">
    <PageHeader title="Pod 状态" subtitle="资源使用概览（数据为示例占位）。" />

    <div class="stat-row">
      <div class="stat-card success">
        <div class="stat-value">{{ stats.running }}</div>
        <div class="stat-label">Running Pods</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.cpu }}</div>
        <div class="stat-label">CPU Cores</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.memory }}</div>
        <div class="stat-label">Memory MiB</div>
      </div>
      <div class="stat-card accent">
        <div class="stat-value">{{ stats.storage }}</div>
        <div class="stat-label">Storage GiB</div>
      </div>
    </div>

    <div v-if="pods.length === 0" class="empty-wrap">
      <AppEmpty icon="📦" title="暂无 Pod" description="当前没有可展示的 Pod 状态。" />
    </div>
    <div v-else class="pod-grid">
      <article v-for="p in pods" :key="p.name" class="card pod-card">
        <div class="pod-card-header">
          <h3>{{ p.name }}</h3>
          <AppTag :tone="p.status === 'Running' ? 'success' : 'danger'" :pulse="p.status === 'Running'">{{ p.status }}</AppTag>
        </div>
        <div class="metric-row">
          <div class="metric">
            <span class="metric-label">CPU</span>
            <div class="metric-bar"><div class="metric-fill" :style="{ width: p.cpuPct + '%' }"></div></div>
            <span class="metric-value">{{ p.cpuText }}</span>
          </div>
          <div class="metric">
            <span class="metric-label">内存</span>
            <div class="metric-bar"><div class="metric-fill mem" :style="{ width: p.memPct + '%' }"></div></div>
            <span class="metric-value">{{ p.memText }}</span>
          </div>
          <div class="metric">
            <span class="metric-label">存储</span>
            <div class="metric-bar"><div class="metric-fill disk" :style="{ width: p.diskPct + '%' }"></div></div>
            <span class="metric-value">{{ p.diskText }}</span>
          </div>
        </div>
        <div class="pod-footer">
          <span>重启次数: {{ p.restarts }}</span>
          <span>运行时间: {{ p.uptime }}</span>
        </div>
      </article>
    </div>

    <p class="note">* 以上为静态占位数据，实际 Pod 资源监控接口尚未接入。</p>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import AppTag from "../components/ui/AppTag.vue";
import AppEmpty from "../components/ui/AppEmpty.vue";
import PageHeader from "../components/ui/PageHeader.vue";

const pods = ref([
  {
    name: "pyclaw-api", status: "Running",
    cpuPct: 23, cpuText: "0.23 / 1.0 核",
    memPct: 45, memText: "128 / 256 MiB",
    diskPct: 12, diskText: "1.2 / 10 GiB",
    restarts: 0, uptime: "3 天",
  },
  {
    name: "spring-backend", status: "Running",
    cpuPct: 35, cpuText: "0.35 / 1.0 核",
    memPct: 62, memText: "320 / 512 MiB",
    diskPct: 8, diskText: "0.8 / 10 GiB",
    restarts: 0, uptime: "3 天",
  },
  {
    name: "pyclaw-web", status: "Running",
    cpuPct: 5, cpuText: "0.05 / 0.3 核",
    memPct: 28, memText: "72 / 256 MiB",
    diskPct: 4, diskText: "0.4 / 10 GiB",
    restarts: 0, uptime: "3 天",
  },
]);

const stats = computed(() => ({
  running: pods.value.filter(p => p.status === "Running").length,
  cpu: "0.45 / 2.0",
  memory: "384 / 768",
  storage: "2.4 / 5.0",
}));
</script>

<style scoped>
.pod-page { max-width: 1000px; }
.pod-grid { display: grid; gap: 20px; }
.pod-card { display: flex; flex-direction: column; gap: 16px; }
.pod-card-header { display: flex; justify-content: space-between; align-items: center; }
.pod-card-header h3 { font-size: 16px; font-family: var(--font-mono); margin: 0; }
.metric-row { display: grid; gap: 16px; }
.metric { display: grid; grid-template-columns: 60px 1fr 130px; align-items: center; gap: 12px; font-size: 13px; }
.metric-label { color: var(--text-muted); }
.metric-bar { height: 8px; background: var(--bg-tertiary); border-radius: 4px; overflow: hidden; }
.metric-fill { height: 100%; background: var(--accent); border-radius: 4px; transition: width 0.5s var(--ease-out); }
.metric-fill.mem { background: var(--warning); }
.metric-fill.disk { background: var(--success); }
.metric-value { color: var(--text-secondary); text-align: right; font-family: var(--font-mono); font-size: 12px; }
.pod-footer { display: flex; gap: 24px; padding-top: 12px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); }
.empty-wrap { margin-top: 24px; }
.note { margin-top: 24px; color: var(--text-muted); font-size: 12px; font-style: italic; }
</style>
