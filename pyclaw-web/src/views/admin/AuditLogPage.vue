<template>
  <div class="page">
    <div class="page-header">
      <h1>审计日志</h1>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
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
            <td><span class="action-tag">{{ log.action }}</span></td>
            <td>{{ log.resourceType }}</td>
            <td class="mono">{{ log.resourceId }}</td>
            <td>
              <span class="status-tag" :class="log.success ? 'success' : 'fail'">{{ log.success ? "成功" : "失败" }}</span>
            </td>
            <td class="err-msg">{{ log.errorMessage || "—" }}</td>
          </tr>
          <tr v-if="logs.length === 0">
            <td colspan="7" class="empty">暂无审计日志</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { api } from "../../api/client.js";

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
.page { max-width: 1200px; }
.table-wrap { overflow-x: auto; }
.log-time { white-space: nowrap; font-family: "JetBrains Mono", monospace; font-size: 12px; }
.action-tag { font-size: 11px; padding: 1px 8px; background: var(--accent-glow); color: var(--accent); border-radius: 10px; font-family: monospace; }
.mono { font-family: "JetBrains Mono", monospace; font-size: 12px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; }
.status-tag { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.status-tag.success { background: rgba(63,185,80,0.12); color: var(--success); }
.status-tag.fail { background: rgba(248,81,73,0.1); color: var(--danger); }
.err-msg { max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-muted); }
</style>
