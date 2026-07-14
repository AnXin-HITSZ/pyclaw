<template>
  <div class="page">
    <div class="page-header">
      <h1>工具目录</h1>
      <span class="subtitle">查看系统中可用的 Agent 工具</span>
    </div>

    <div class="tabs">
      <button
        v-for="p in profiles"
        :key="p"
        class="tab"
        :class="{ active: activeProfile === p }"
        @click="activeProfile = p"
      >{{ p }}</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>
    <div v-else class="tool-table-wrap">
      <table class="tool-table">
        <thead>
          <tr>
            <th>工具名称</th>
            <th>描述</th>
            <th>分类</th>
            <th>Shell 审批</th>
            <th>只读</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tool in filteredTools" :key="tool.name">
            <td class="tool-name">{{ tool.name }}</td>
            <td class="tool-desc">{{ tool.description || "—" }}</td>
            <td><span class="tag">{{ tool.category || "general" }}</span></td>
            <td><span class="tag" :class="tool.shellApproval || 'none'">{{ tool.shellApproval || "none" }}</span></td>
            <td>{{ tool.readonly ? "是" : "否" }}</td>
          </tr>
          <tr v-if="filteredTools.length === 0">
            <td colspan="5" class="no-data">暂无工具</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { api } from "../api/client.js";

const catalog = ref([]);
const profiles = ref([]);
const loading = ref(true);
const error = ref("");
const activeProfile = ref("");

async function load() {
  try {
    const [c, p] = await Promise.all([
      api.get("/api/tools/catalog"),
      api.get("/api/tools/profiles"),
    ]);
    catalog.value = c;
    profiles.value = p;
    if (p.length) activeProfile.value = p[0];
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

const filteredTools = computed(() => {
  if (!activeProfile.value) return catalog.value;
  return catalog.value.filter(t => {
    if (!t.profiles) return false;
    return t.profiles.includes(activeProfile.value);
  });
});

onMounted(load);
</script>

<style scoped>
.page { max-width: 1100px; }
.page-header { margin-bottom: 16px; }
.page-header h1 { font-size: 24px; }
.subtitle { color: var(--text-secondary); font-size: 14px; margin-left: 12px; }
.tabs { display: flex; gap: 8px; margin-bottom: 20px; }
.tab {
  padding: 6px 16px; font-size: 13px; color: var(--text-secondary);
  background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 6px;
  transition: all 0.15s;
}
.tab:hover { color: var(--text-primary); }
.tab.active { background: rgba(88,166,255,0.1); color: var(--accent); border-color: var(--accent); }
.tool-table-wrap { overflow-x: auto; }
.tool-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.tool-table th { text-align: left; padding: 10px 12px; background: var(--bg-secondary); color: var(--text-secondary); font-weight: 600; border-bottom: 1px solid var(--border-color); }
.tool-table td { padding: 10px 12px; border-bottom: 1px solid var(--border-color); }
.tool-name { font-family: monospace; font-weight: 600; }
.tool-desc { max-width: 400px; color: var(--text-secondary); }
.tag { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: rgba(88,166,255,0.1); color: var(--accent); }
.tag.none { background: rgba(110,118,129,0.15); color: var(--text-muted); }
.loading, .error-msg, .no-data { text-align: center; padding: 48px; color: var(--text-secondary); }
.error-msg { color: var(--danger); }
</style>
