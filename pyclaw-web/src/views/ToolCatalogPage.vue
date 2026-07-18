<template>
  <div class="tool-page">
    <PageHeader title="工具目录" subtitle="查看当前 Claw 运行时可交给 Agent 的工具。" />

    <div class="toolbar">
      <div class="tabs">
        <button
          v-for="p in profiles"
          :key="p"
          class="tab"
          :class="{ active: activeProfile === p }"
          @click="activeProfile = p"
        >{{ p }}</button>
      </div>
    </div>

    <div v-if="loading" class="tool-grid">
      <div v-for="i in 8" :key="i" class="card tool-card skeleton-card">
        <AppSkeleton variant="text" :width="'60%'" :height=16 />
        <AppSkeleton variant="text" :width="'40%'" :height=12 />
        <AppSkeleton variant="text" :width="'90%'" :height=12 />
        <AppSkeleton variant="text" :width="'50%'" :height=12 />
      </div>
    </div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>
    <div v-else-if="effectiveTools.length === 0" class="empty-wrap">
      <AppEmpty icon="🧰" title="当前策略下暂无可用工具" description="切换上方的 Tool Profile 即可查看不同的工具集合。" />
    </div>
    <div v-else class="tool-grid">
      <article v-for="(tool, index) in effectiveTools" :key="tool.name" class="card tool-card" :style="{ transitionDelay: `${index * 30}ms` }">
        <div class="tool-head">
          <h3 class="tool-name">{{ tool.name }}</h3>
          <AppTag :tone="riskTone(tool.risk)">{{ tool.risk || "low" }}</AppTag>
        </div>
        <p class="tool-desc">{{ tool.description || "暂无描述" }}</p>
        <div class="tool-meta">
          <span class="meta-item"><span class="meta-label">分类</span><span class="meta-value">{{ tool.category || tool.sectionId || "general" }}</span></span>
          <span class="meta-item"><span class="meta-label">范围</span><span class="meta-value">{{ tool.executionScope || "claw_sandbox" }}</span></span>
          <span class="meta-item"><span class="meta-label">只读</span><span class="meta-value">{{ tool.readonly ? "是" : "否" }}</span></span>
        </div>
      </article>
    </div>

    <div v-if="!loading && !error && deniedTools.length" class="denied-panel card">
      <h3 class="section-title">当前不可用</h3>
      <div class="denied-list">
        <span v-for="name in deniedTools" :key="name" class="denied-chip">{{ name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { api } from "../api/client.js";
import AppSkeleton from "../components/ui/AppSkeleton.vue";
import AppTag from "../components/ui/AppTag.vue";
import AppEmpty from "../components/ui/AppEmpty.vue";
import PageHeader from "../components/ui/PageHeader.vue";

const catalog = ref([]);
const profiles = ref([]);
const effectiveNames = ref([]);
const deniedTools = ref([]);
const loading = ref(true);
const resolving = ref(false);
const error = ref("");
const activeProfile = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [c, p] = await Promise.all([
      api.get("/api/tools/catalog"),
      api.get("/api/tools/profiles"),
    ]);
    catalog.value = c;
    profiles.value = p;
    if (p.length) activeProfile.value = p.includes("coding") ? "coding" : p[0];
    await resolveEffective();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function resolveEffective() {
  if (!activeProfile.value) return;
  resolving.value = true;
  try {
    const result = await api.post("/api/tools/effective", {
      profile: activeProfile.value,
      deny: [],
      alsoAllow: [],
      readonly: activeProfile.value === "readonly",
    });
    effectiveNames.value = result.effectiveTools || [];
    deniedTools.value = result.deniedTools || [];
  } catch (e) {
    error.value = e.message;
  } finally {
    resolving.value = false;
  }
}

const effectiveTools = computed(() => {
  const names = new Set(effectiveNames.value);
  return catalog.value.filter(tool => names.has(tool.name));
});

function riskTone(risk) {
  if (risk === "high") return "danger";
  if (risk === "medium") return "warning";
  return "success";
}

watch(activeProfile, () => {
  if (!loading.value) resolveEffective();
});

onMounted(load);
</script>

<style scoped>
.tool-page { max-width: 1200px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; flex-wrap: wrap; }
.tabs { display: flex; gap: 8px; flex-wrap: wrap; }
.tab { padding: 6px 16px; font-size: 13px; color: var(--text-secondary); background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 999px; cursor: pointer; transition: all 0.15s var(--ease-out); }
.tab:hover { color: var(--text-primary); }
.tab.active { background: rgba(240,163,58,0.1); color: var(--accent); border-color: var(--accent); }
.tool-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.tool-card { display: flex; flex-direction: column; gap: 8px; animation: card-in 0.4s var(--ease-out) both; }
@keyframes card-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.skeleton-card { gap: 10px; padding: 22px; }
.tool-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.tool-name { font-family: var(--font-mono); font-size: 14px; font-weight: 600; margin: 0; }
.tool-desc { font-size: 12px; color: var(--text-secondary); margin: 0; line-height: 1.5; }
.tool-meta { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 4px; font-size: 11px; }
.meta-item { display: inline-flex; align-items: center; gap: 4px; }
.meta-label { color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.4px; }
.meta-value { color: var(--text-secondary); font-family: var(--font-mono); }
.empty-wrap { margin-top: 24px; }
.error-msg { text-align: center; padding: 48px; color: var(--danger); }
.denied-panel { margin-top: 20px; }
.denied-list { display: flex; flex-wrap: wrap; gap: 8px; }
.denied-chip { font-family: var(--font-mono); font-size: 12px; color: var(--text-muted); border: 1px solid var(--border); border-radius: 999px; padding: 2px 10px; }
</style>
