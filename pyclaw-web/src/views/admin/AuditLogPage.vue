<template>
  <div class="audit-page">
    <PageHeader title="审计日志" subtitle="记录系统中所有关键操作的执行轨迹。" />

    <div v-if="loading" class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>时间</th><th>操作者</th><th>操作</th><th>资源类型</th><th>资源 ID</th><th>结果</th><th>消息</th></tr>
        </thead>
        <tbody>
          <tr v-for="i in 6" :key="i"><td colspan="7"><AppSkeleton variant="text" :width="'100%'" :height=14 /></td></tr>
        </tbody>
      </table>
    </div>
    <div v-else-if="logs.length === 0" class="empty-wrap">
      <AppEmpty icon="📜" title="暂无审计日志" description="系统关键操作发生后将在此留痕。" />
    </div>
    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作者</th>
            <th>操作</th>
            <th>资源类型</th>
            <th>资源 ID</th>
            <th>结果</th>
            <th>消息</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="log-time">{{ formatDate(log.createdAt) }}</td>
            <td>{{ log.actorType }} ({{ log.actorId }})</td>
            <td><code class="action-tag">{{ log.action }}</code></td>
            <td>{{ log.resourceType }}</td>
            <td class="mono">{{ log.resourceId }}</td>
            <td>
              <AppTag :tone="log.success ? 'success' : 'danger'">{{ log.success ? "成功" : "失败" }}</AppTag>
            </td>
            <td class="err-msg">{{ log.errorMessage || "—" }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { api } from "../../api/client.js";
import AppSkeleton from "../../components/ui/AppSkeleton.vue";
import AppTag from "../../components/ui/AppTag.vue";
import AppEmpty from "../../components/ui/AppEmpty.vue";
import PageHeader from "../../components/ui/PageHeader.vue";

const logs = ref([]);
const loading = ref(true);

async function load() {
  try { logs.value = await api.get("/api/audit-logs"); } catch {}
  finally { loading.value = false; }
}

function formatDate(s) { return s ? new Date(s).toLocaleString("zh-CN") : "—"; }

onMounted(load);
</script>

<style scoped>
.audit-page { max-width: 1200px; }
.table-wrap { overflow-x: auto; }
.data-table thead th { position: sticky; top: 0; z-index: 2; }
.log-time { white-space: nowrap; font-family: var(--font-mono); font-size: 12px; }
.action-tag { font-family: var(--font-mono); font-size: 11px; color: var(--accent); }
.mono { font-family: var(--font-mono); font-size: 12px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; }
.err-msg { max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-muted); }
.empty-wrap { margin-top: 24px; }
</style>
