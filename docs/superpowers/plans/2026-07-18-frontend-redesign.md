# PyClaw 前端界面重设计实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在功能完全不变的前提下，将 pyclaw-web 全部 18 个页面重设计为「深空未来感」风格，并补齐三层加载体系（路由进度条 + 骨架屏 + 按钮 spinner）。

**Architecture:** 纯手写 CSS 设计系统（不引入 UI 组件库）。先重写全局设计 tokens 与共享组件库（`src/components/ui/`），再逐页替换模板与样式。所有页面的 `<script setup>` 业务逻辑保持不变，仅允许新增 UI 态 ref（如 `submitting`）与 Toast 调用。

**Tech Stack:** Vue 3.5 + Vite 6 + vue-router 4，无测试框架，验证方式为 `npm run build` + 人工走查。

**设计规格:** `docs/superpowers/specs/2026-07-18-frontend-redesign-design.md`（实施前必读）

## Global Constraints

- 不修改 `src/api/client.js`、`src/composables/useAuth.js` 的任何逻辑。
- `src/router/index.js` 仅允许追加进度条监听，不得改动守卫行为与路由表。
- 不新增任何 npm 运行时依赖（字体走 Google Fonts CDN）。
- 每个页面的 API 调用、表单字段、权限判断、跳转逻辑保持原样。
- 所有动效必须包在 `@media (prefers-reduced-motion: no-preference)` 内或提供降级。
- 工作目录：`pyclaw-web/`。构建命令：`cd pyclaw-web && npm run build`。
- 每个任务完成后提交一次 commit，commit message 用中文 conventional 格式。

---

### Task 1: 设计 Tokens 与全局样式重写

**Files:**
- Modify: `pyclaw-web/index.html`（第 9 行字体链接）
- Modify: `pyclaw-web/src/App.vue`（仅 `<style>` 块，模板不动）

**Interfaces:**
- Consumes: 无
- Produces: 全局 CSS 变量（`--bg-abyss`、`--accent-2`、`--accent-3`、`--gradient-aurora`、`--glow-accent`、`--glow-cyan`、`--font-display` 等）与全局类（`.btn-primary`、`.btn-secondary`、`.card`、`.form-group`、`.modal-overlay`、`.modal`、`.data-table`、`.empty-state`、`.stat-row`、`.stat-card`、`.section-title`、`.skeleton`、`.status-tag`、`.page`、`.page-header`、`.switch-*`）。后续所有任务依赖这些名字，不得改名。

- [ ] **Step 1: index.html 加入 Space Grotesk 字体**

将第 9 行替换为：

```html
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500&family=Space+Grotesk:wght@500;600;700&display=swap" rel="stylesheet" />
```

- [ ] **Step 2: 重写 App.vue 的 `<style>` 块**

模板（第 1-11 行）保持不变。将整个 `<style>` 块替换为：

```css
/* ── Design Tokens: Deep Space Console ── */
:root {
  --bg-abyss: #05070c;
  --bg-deep: #0a0e16;
  --bg-surface: #10151f;
  --bg-raised: #161c29;
  --bg-hover: #1b2230;
  --border: #232b3a;
  --border-light: #2e3850;
  --text-primary: #e6eaf2;
  --text-secondary: #8b96ab;
  --text-muted: #5b6579;
  --accent: #f5a83d;
  --accent-soft: #d98f2b;
  --accent-2: #4dd0e1;
  --accent-3: #8b7cf6;
  --gradient-aurora: linear-gradient(135deg, #f5a83d, #e0637c 45%, #8b7cf6);
  --accent-glow: rgba(245, 168, 61, 0.14);
  --accent-glow-strong: rgba(245, 168, 61, 0.28);
  --glow-accent: 0 0 24px rgba(245, 168, 61, 0.25);
  --glow-cyan: 0 0 24px rgba(77, 208, 225, 0.18);
  --success: #3fce6c;
  --danger: #ff5c5c;
  --warning: #e0a832;
  --info: #4dd0e1;
  --radius-sm: 8px;
  --radius: 14px;
  --radius-lg: 18px;
  --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.35);
  --shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
  --shadow-glow: var(--glow-accent);
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
  --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
  --font-display: "Space Grotesk", "Inter", -apple-system, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, monospace;
  --topbar-height: 56px;
  --content-max-width: 1100px;
  --content-gutter: 48px;
  --card-padding: 24px;
  --section-gap: 24px;
  /* Backward-compatible aliases（旧页面未改完前不报错） */
  --bg-primary: var(--bg-deep);
  --bg-secondary: var(--bg-surface);
  --bg-tertiary: var(--bg-raised);
  --border-color: var(--border);
  --accent-hover: var(--accent-soft);
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
  background-color: var(--bg-abyss);
  color: var(--text-primary);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 全局深空背景：细网格 + 顶部极光光晕 */
body::before {
  content: "";
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 40% at 70% -5%, rgba(245, 168, 61, 0.10), transparent 60%),
    radial-gradient(ellipse 50% 35% at 15% 0%, rgba(139, 124, 246, 0.08), transparent 60%),
    radial-gradient(ellipse 45% 30% at 45% 110%, rgba(77, 208, 225, 0.05), transparent 60%),
    linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    var(--bg-abyss);
  background-size: auto, auto, auto, 44px 44px, 44px 44px, auto;
}

a { color: var(--accent); text-decoration: none; transition: color 0.2s var(--ease-out); }
a:hover { color: var(--accent-soft); }
button { cursor: pointer; font-family: inherit; }
input, textarea, select { font-family: inherit; }

::selection { background: rgba(245, 168, 61, 0.28); }

/* ── Scrollbar ── */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: var(--border-light); }

/* ── Page transition ── */
.page-enter-active { transition: opacity 0.22s var(--ease-out), transform 0.22s var(--ease-out); }
.page-leave-active { transition: opacity 0.14s var(--ease-out), transform 0.14s var(--ease-out); }
.page-enter-from { opacity: 0; transform: translateY(10px); }
.page-leave-to { opacity: 0; transform: translateY(-4px); }

/* ── Staggered card enter ── */
.stagger-enter-active { transition: opacity 0.4s var(--ease-out), transform 0.4s var(--ease-out); }
.stagger-enter-from { opacity: 0; transform: translateY(16px); }

/* ── Shared base styles ── */
.page { max-width: 1000px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.page-header h1 {
  font-family: var(--font-display);
  font-size: 24px; font-weight: 700; letter-spacing: -0.02em; flex: 1;
}

.btn-primary {
  padding: 9px 22px; font-size: 14px; font-weight: 600; color: #0a0e14;
  background: linear-gradient(135deg, var(--accent), var(--accent-soft));
  border: none; border-radius: 10px;
  transition: all 0.22s var(--ease-out);
  position: relative; overflow: hidden;
}
.btn-primary::after {
  content: ""; position: absolute; inset: 0;
  background: linear-gradient(135deg, transparent 40%, rgba(255, 255, 255, 0.16) 50%, transparent 60%);
  transform: translateX(-100%); transition: transform 0.45s var(--ease-out);
}
.btn-primary:hover::after { transform: translateX(100%); }
.btn-primary:hover { transform: translateY(-1px); box-shadow: var(--glow-accent); }
.btn-primary:active { transform: translateY(0); }
.btn-primary:disabled { opacity: 0.4; pointer-events: none; }

.btn-secondary {
  padding: 9px 22px; font-size: 14px; color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border); border-radius: 10px;
  transition: all 0.22s var(--ease-out);
}
.btn-secondary:hover { background: var(--bg-raised); border-color: var(--border-light); color: var(--text-primary); }

.btn-back {
  padding: 6px 14px; font-size: 13px; color: var(--text-muted);
  background: transparent; border: 1px solid var(--border); border-radius: 10px;
  transition: all 0.2s var(--ease-out);
}
.btn-back:hover { color: var(--text-secondary); border-color: var(--border-light); background: var(--bg-surface); }

.card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 40%), var(--bg-surface);
  border: 1px solid var(--border); border-radius: var(--radius);
  padding: var(--card-padding); transition: all 0.28s var(--ease-out);
  position: relative; box-shadow: var(--shadow-sm);
}
.card::before {
  content: ""; position: absolute; top: 0; left: 14px; right: 14px; height: 1px;
  background: var(--gradient-aurora);
  opacity: 0; transition: opacity 0.3s var(--ease-out);
}
.card:hover { border-color: var(--border-light); transform: translateY(-2px); box-shadow: var(--shadow); }
.card:hover::before { opacity: 0.7; }

.card h3 { font-size: 15px; font-weight: 600; margin-bottom: 16px; letter-spacing: -0.01em; }

.status-tag { font-size: 11px; padding: 3px 11px; border-radius: 999px; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }
.status-tag.active { background: rgba(63, 206, 108, 0.12); color: var(--success); }
.status-tag.inactive { background: rgba(255, 92, 92, 0.1); color: var(--danger); }

.loading, .no-data { text-align: center; padding: 48px; color: var(--text-muted); font-size: 14px; }
.error-msg { text-align: center; padding: 48px; color: var(--danger); font-size: 14px; }

.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 13px; color: var(--text-secondary); font-weight: 500; }
.form-group input, .form-group textarea, .form-group select {
  width: 100%; padding: 10px 14px; background: var(--bg-deep); border: 1px solid var(--border);
  border-radius: 10px; color: var(--text-primary); font-size: 14px;
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}
.form-group input:focus, .form-group textarea:focus, .form-group select:focus {
  outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow);
}

.modal-overlay {
  position: fixed; inset: 0; background: rgba(3, 5, 9, 0.72); display: flex;
  align-items: center; justify-content: center; z-index: 100;
  backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
}
.modal {
  background: var(--bg-surface); border: 1px solid var(--border-light); border-radius: var(--radius-lg);
  padding: 32px; width: 480px; max-width: 90vw;
  box-shadow: var(--shadow), 0 0 40px rgba(139, 124, 246, 0.08);
  animation: modal-enter 0.28s var(--ease-spring);
}
.modal h2 { margin-bottom: 20px; font-family: var(--font-display); font-weight: 700; letter-spacing: -0.02em; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }

label.switch-line {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  margin: 4px 0 16px; color: var(--text-secondary); font-size: 13px; font-weight: 600; cursor: pointer;
}
.switch-label { min-width: 0; }
.switch-input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.switch-track {
  position: relative; width: 36px; height: 20px; flex: 0 0 auto; border-radius: 999px;
  background: var(--bg-deep); border: 1px solid var(--border-light);
  transition: background 0.18s var(--ease-out), border-color 0.18s var(--ease-out), box-shadow 0.18s var(--ease-out);
}
.switch-track::after {
  content: ""; position: absolute; top: 2px; left: 2px; width: 14px; height: 14px; border-radius: 999px;
  background: var(--text-secondary);
  transition: transform 0.18s var(--ease-out), background 0.18s var(--ease-out);
}
.switch-input:checked + .switch-track { background: var(--accent); border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
.switch-input:checked + .switch-track::after { transform: translateX(16px); background: #fff; }
.switch-input:focus-visible + .switch-track { outline: 2px solid var(--accent); outline-offset: 2px; }

@keyframes modal-enter {
  from { opacity: 0; transform: scale(0.95) translateY(10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

/* ── Skeleton shimmer ── */
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
.skeleton {
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--bg-raised) 25%, var(--bg-hover) 50%, var(--bg-raised) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.6s var(--ease-in-out) infinite;
}

/* ── Table base ── */
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th {
  text-align: left; padding: 11px 14px; background: var(--bg-surface);
  color: var(--text-muted); font-weight: 600; font-size: 11px;
  text-transform: uppercase; letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border);
}
.data-table td { padding: 11px 14px; border-bottom: 1px solid var(--border); color: var(--text-primary); }
.data-table tbody tr { transition: background 0.15s var(--ease-out); }
.data-table tbody tr:hover { background: var(--bg-hover); }
.data-table tbody tr:nth-child(even) { background: rgba(255, 255, 255, 0.015); }
.data-table tbody tr:nth-child(even):hover { background: var(--bg-hover); }

/* ── Empty state ── */
.empty-state { text-align: center; padding: 64px 24px; }
.empty-state-icon { font-size: 44px; margin-bottom: 16px; opacity: 0.5; }
.empty-state h3 { font-size: 17px; font-weight: 600; margin-bottom: 6px; color: var(--text-secondary); }
.empty-state p { font-size: 14px; color: var(--text-muted); max-width: 420px; margin: 0 auto 20px; line-height: 1.6; }

/* ── Stat cards row ── */
.stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: var(--section-gap); }
.stat-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 50%), var(--bg-surface);
  border: 1px solid var(--border); border-radius: var(--radius);
  padding: 20px 24px; box-shadow: var(--shadow-sm);
  transition: all 0.22s var(--ease-out);
}
.stat-card:hover { border-color: var(--border-light); box-shadow: var(--shadow); transform: translateY(-2px); }
.stat-value { font-family: var(--font-display); font-size: 30px; font-weight: 700; letter-spacing: -0.02em; line-height: 1.1; font-variant-numeric: tabular-nums; }
.stat-label { font-size: 12px; color: var(--text-muted); margin-top: 4px; font-weight: 500; text-transform: uppercase; letter-spacing: 0.4px; }
.stat-card.accent .stat-value { color: var(--accent); }
.stat-card.success .stat-value { color: var(--success); }
.stat-card.danger .stat-value { color: var(--danger); }

/* ── Section divider ── */
.section-title {
  font-size: 13px; font-weight: 700; color: var(--text-muted);
  text-transform: uppercase; letter-spacing: 0.6px;
  margin-bottom: 16px; padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

/* ── Reduced motion ── */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
}
```

- [ ] **Step 3: 构建验证**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功，无 CSS 语法错误。

- [ ] **Step 4: Commit**

```bash
git add pyclaw-web/index.html pyclaw-web/src/App.vue
git commit -m "feat(web-ui): 重写全局设计 tokens 为深空未来感（极光渐变/网格背景/发光体系）"
```

---

### Task 2: 基础组件 — AppSpinner / AppSkeleton / AppButton

**Files:**
- Create: `pyclaw-web/src/components/ui/AppSpinner.vue`
- Create: `pyclaw-web/src/components/ui/AppSkeleton.vue`
- Create: `pyclaw-web/src/components/ui/AppButton.vue`

**Interfaces:**
- Consumes: Task 1 的全局 tokens
- Produces:
  - `<AppSpinner size="sm|md|lg" />`（默认 `md`）
  - `<AppSkeleton variant="text|rect|circle" :width="..." :height="..." :lines="n" />`
  - `<AppButton variant="primary|ghost|danger" :loading="bool" :disabled="bool" type="button|submit">`；loading 时禁用并显示 spinner。后续页面任务统一用 `<AppButton>` 替换原生 `.btn-primary` / `.btn-secondary` 提交按钮。

- [ ] **Step 1: AppSpinner.vue**

```vue
<template>
  <span class="app-spinner" :class="`size-${size}`" role="status" aria-label="加载中"></span>
</template>

<script setup>
defineProps({
  size: { type: String, default: "md", validator: v => ["sm", "md", "lg"].includes(v) },
});
</script>

<style scoped>
.app-spinner {
  display: inline-block;
  border-radius: 50%;
  border: 2px solid rgba(245, 168, 61, 0.2);
  border-top-color: var(--accent);
  animation: spin 0.7s linear infinite;
}
.size-sm { width: 14px; height: 14px; border-width: 2px; }
.size-md { width: 20px; height: 20px; }
.size-lg { width: 32px; height: 32px; border-width: 3px; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
```

- [ ] **Step 2: AppSkeleton.vue**

```vue
<template>
  <div class="app-skeleton" :class="`variant-${variant}`" :style="styleObj" aria-hidden="true">
    <template v-if="variant === 'text' && lines > 1">
      <div v-for="i in lines" :key="i" class="skeleton-line skeleton" :style="{ width: i === lines ? '60%' : '100%' }"></div>
    </template>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  variant: { type: String, default: "rect", validator: v => ["text", "rect", "circle"].includes(v) },
  width: { type: [String, Number], default: "100%" },
  height: { type: [String, Number], default: 16 },
  lines: { type: Number, default: 1 },
});

function toCss(v) { return typeof v === "number" ? `${v}px` : v; }

const styleObj = computed(() => {
  if (props.variant === "circle") {
    const size = toCss(props.width === "100%" ? 40 : props.width);
    return { width: size, height: size, borderRadius: "50%" };
  }
  if (props.variant === "text" && props.lines > 1) return { width: toCss(props.width) };
  return { width: toCss(props.width), height: toCss(props.height) };
});
</script>

<style scoped>
.app-skeleton { display: block; }
.variant-rect, .variant-circle, .variant-text:not(.app-skeleton:has(.skeleton-line)) {
  background: linear-gradient(90deg, var(--bg-raised) 25%, var(--bg-hover) 50%, var(--bg-raised) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.6s var(--ease-in-out) infinite;
  border-radius: var(--radius-sm);
}
.variant-circle { border-radius: 50%; }
.skeleton-line { height: 14px; margin-bottom: 10px; }
.skeleton-line:last-child { margin-bottom: 0; }
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>
```

- [ ] **Step 3: AppButton.vue**

```vue
<template>
  <button
    class="app-button"
    :class="`variant-${variant}`"
    :type="type"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <AppSpinner v-if="loading" size="sm" class="btn-spinner" />
    <span v-if="loading" class="btn-loading-text">{{ loadingText }}</span>
    <slot v-else />
  </button>
</template>

<script setup>
import AppSpinner from "./AppSpinner.vue";

defineProps({
  variant: { type: String, default: "primary", validator: v => ["primary", "ghost", "danger"].includes(v) },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: "处理中..." },
  disabled: { type: Boolean, default: false },
  type: { type: String, default: "button" },
});
defineEmits(["click"]);
</script>

<style scoped>
.app-button {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  padding: 9px 22px; font-size: 14px; font-weight: 600; border-radius: 10px;
  border: 1px solid transparent; font-family: inherit; cursor: pointer;
  transition: all 0.22s var(--ease-out); position: relative; overflow: hidden;
}
.variant-primary { color: #0a0e14; background: linear-gradient(135deg, var(--accent), var(--accent-soft)); }
.variant-primary::after {
  content: ""; position: absolute; inset: 0;
  background: linear-gradient(135deg, transparent 40%, rgba(255, 255, 255, 0.16) 50%, transparent 60%);
  transform: translateX(-100%); transition: transform 0.45s var(--ease-out);
}
.variant-primary:hover:not(:disabled)::after { transform: translateX(100%); }
.variant-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: var(--glow-accent); }
.variant-ghost { color: var(--text-secondary); background: rgba(255, 255, 255, 0.03); border-color: var(--border); }
.variant-ghost:hover:not(:disabled) { background: var(--bg-raised); border-color: var(--border-light); color: var(--text-primary); }
.variant-danger { color: #fff; background: linear-gradient(135deg, #e5484d, #c93a3f); }
.variant-danger:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 0 20px rgba(255, 92, 92, 0.3); }
.app-button:active:not(:disabled) { transform: translateY(0); }
.app-button:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-spinner :deep(.app-spinner) { border-color: rgba(10, 14, 20, 0.25); border-top-color: currentColor; }
.variant-ghost .btn-spinner :deep(.app-spinner) { border-color: rgba(245, 168, 61, 0.2); border-top-color: var(--accent); }
.btn-loading-text { white-space: nowrap; }
</style>
```

- [ ] **Step 4: 构建验证**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

- [ ] **Step 5: Commit**

```bash
git add pyclaw-web/src/components/ui/
git commit -m "feat(web-ui): 新增基础组件 AppSpinner/AppSkeleton/AppButton"
```

---

### Task 3: 反馈组件 — useToast / AppToast / AppModal / AppEmpty / AppTag

**Files:**
- Create: `pyclaw-web/src/composables/useToast.js`
- Create: `pyclaw-web/src/components/ui/AppToast.vue`
- Create: `pyclaw-web/src/components/ui/AppModal.vue`
- Create: `pyclaw-web/src/components/ui/AppEmpty.vue`
- Create: `pyclaw-web/src/components/ui/AppTag.vue`
- Modify: `pyclaw-web/src/App.vue`（模板内挂载 `<AppToast />`）

**Interfaces:**
- Consumes: Task 1 tokens、Task 2 AppSpinner（AppModal 不需要）
- Produces:
  - `useToast()` → `{ toast }`，`toast.success(msg)` / `toast.error(msg)` / `toast.info(msg)`，3.5s 自动消失
  - `<AppModal :show="bool" title="..." @close="...">` 默认插槽 + `#actions` 插槽；Esc 与点遮罩关闭
  - `<AppEmpty icon="..." title="..." description="...">` + 默认插槽（放操作按钮）
  - `<AppTag tone="success|danger|warning|info|neutral" :pulse="bool">`
  - App.vue 中全局挂载 `<AppToast />`（后续任务直接用 `useToast`）

- [ ] **Step 1: useToast.js**

```js
import { reactive, readonly } from "vue";

let seq = 0;
const toasts = reactive([]);

function push(type, message, duration = 3500) {
  const id = ++seq;
  toasts.push({ id, type, message });
  setTimeout(() => {
    const idx = toasts.findIndex(t => t.id === id);
    if (idx !== -1) toasts.splice(idx, 1);
  }, duration);
}

export function useToast() {
  return {
    toasts: readonly(toasts),
    toast: {
      success: msg => push("success", msg),
      error: msg => push("error", msg),
      info: msg => push("info", msg),
    },
  };
}

// 内部使用：AppToast 渲染需要可写数组
export function useToastStore() {
  return { toasts };
}
```

- [ ] **Step 2: AppToast.vue**

```vue
<template>
  <Teleport to="body">
    <div class="toast-container" aria-live="polite">
      <TransitionGroup name="toast">
        <div v-for="t in toasts" :key="t.id" class="toast" :class="`toast-${t.type}`">
          <span class="toast-dot"></span>
          <span class="toast-msg">{{ t.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { useToastStore } from "../../composables/useToast.js";

const { toasts } = useToastStore();
</script>

<style scoped>
.toast-container {
  position: fixed; top: 20px; right: 20px; z-index: 1000;
  display: flex; flex-direction: column; gap: 10px; pointer-events: none;
}
.toast {
  display: flex; align-items: center; gap: 10px;
  min-width: 240px; max-width: 380px; padding: 12px 16px;
  background: rgba(16, 21, 31, 0.92); border: 1px solid var(--border-light);
  border-radius: 12px; box-shadow: var(--shadow);
  backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  font-size: 13px; color: var(--text-primary); pointer-events: auto;
}
.toast-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 auto; }
.toast-success .toast-dot { background: var(--success); box-shadow: 0 0 8px var(--success); }
.toast-error .toast-dot { background: var(--danger); box-shadow: 0 0 8px var(--danger); }
.toast-info .toast-dot { background: var(--info); box-shadow: 0 0 8px var(--info); }
.toast-success { border-color: rgba(63, 206, 108, 0.35); }
.toast-error { border-color: rgba(255, 92, 92, 0.35); }
.toast-info { border-color: rgba(77, 208, 225, 0.35); }
.toast-enter-active { transition: all 0.3s var(--ease-spring); }
.toast-leave-active { transition: all 0.2s var(--ease-out); }
.toast-enter-from { opacity: 0; transform: translateX(24px); }
.toast-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
```

- [ ] **Step 3: AppModal.vue**

```vue
<template>
  <Teleport to="body">
    <div v-if="show" class="app-modal-overlay" @click.self="$emit('close')" @keydown.esc="$emit('close')">
      <div class="app-modal" role="dialog" aria-modal="true" :aria-label="title">
        <h2 v-if="title">{{ title }}</h2>
        <slot />
        <div v-if="$slots.actions" class="app-modal-actions">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { watch } from "vue";

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: "" },
});
const emit = defineEmits(["close"]);

function onKeydown(e) { if (e.key === "Escape") emit("close"); }

watch(() => props.show, v => {
  if (v) window.addEventListener("keydown", onKeydown);
  else window.removeEventListener("keydown", onKeydown);
});
</script>

<style scoped>
.app-modal-overlay {
  position: fixed; inset: 0; background: rgba(3, 5, 9, 0.72);
  display: flex; align-items: center; justify-content: center; z-index: 100;
  backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
}
.app-modal {
  background: var(--bg-surface); border: 1px solid var(--border-light);
  border-radius: var(--radius-lg); padding: 32px; width: 480px; max-width: 90vw;
  max-height: 85vh; overflow-y: auto;
  box-shadow: var(--shadow), 0 0 40px rgba(139, 124, 246, 0.08);
  animation: modal-enter 0.28s var(--ease-spring);
}
.app-modal h2 { margin-bottom: 20px; font-family: var(--font-display); font-weight: 700; letter-spacing: -0.02em; }
.app-modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
@keyframes modal-enter {
  from { opacity: 0; transform: scale(0.95) translateY(10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
</style>
```

- [ ] **Step 4: AppEmpty.vue**

```vue
<template>
  <div class="app-empty">
    <div class="app-empty-icon">{{ icon }}</div>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  icon: { type: String, default: "◇" },
  title: { type: String, required: true },
  description: { type: String, default: "" },
});
</script>

<style scoped>
.app-empty { text-align: center; padding: 64px 24px; }
.app-empty-icon { font-size: 44px; margin-bottom: 16px; opacity: 0.5; }
.app-empty h3 { font-size: 17px; font-weight: 600; margin-bottom: 6px; color: var(--text-secondary); }
.app-empty p { font-size: 14px; color: var(--text-muted); max-width: 420px; margin: 0 auto 20px; line-height: 1.6; }
</style>
```

- [ ] **Step 5: AppTag.vue**

```vue
<template>
  <span class="app-tag" :class="`tone-${tone}`">
    <span class="tag-dot" :class="{ pulse }"></span>
    <slot />
  </span>
</template>

<script setup>
defineProps({
  tone: { type: String, default: "neutral", validator: v => ["success", "danger", "warning", "info", "neutral"].includes(v) },
  pulse: { type: Boolean, default: false },
});
</script>

<style scoped>
.app-tag {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 11px; padding: 3px 11px; border-radius: 999px; font-weight: 600;
}
.tag-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.tag-dot.pulse { animation: tag-pulse 1.8s var(--ease-in-out) infinite; }
@keyframes tag-pulse {
  0%, 100% { box-shadow: 0 0 0 0 currentColor; opacity: 1; }
  50% { box-shadow: 0 0 0 4px transparent; opacity: 0.6; }
}
.tone-success { background: rgba(63, 206, 108, 0.12); color: var(--success); }
.tone-danger { background: rgba(255, 92, 92, 0.1); color: var(--danger); }
.tone-warning { background: rgba(224, 168, 50, 0.12); color: var(--warning); }
.tone-info { background: rgba(77, 208, 225, 0.1); color: var(--info); }
.tone-neutral { background: rgba(139, 150, 171, 0.12); color: var(--text-secondary); }
</style>
```

- [ ] **Step 6: App.vue 挂载 AppToast**

App.vue 模板改为：

```vue
<template>
  <router-view v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
  <AppToast />
</template>

<script setup>
import AppToast from "./components/ui/AppToast.vue";
</script>
```

- [ ] **Step 7: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/composables/useToast.js pyclaw-web/src/components/ui/ pyclaw-web/src/App.vue
git commit -m "feat(web-ui): 新增反馈组件 useToast/AppToast/AppModal/AppEmpty/AppTag"
```

---

### Task 4: AppCard / PageHeader / RouteProgress

**Files:**
- Create: `pyclaw-web/src/components/ui/AppCard.vue`
- Create: `pyclaw-web/src/components/ui/PageHeader.vue`
- Create: `pyclaw-web/src/components/ui/RouteProgress.vue`
- Modify: `pyclaw-web/src/App.vue`（挂载 `<RouteProgress />`）

**Interfaces:**
- Consumes: Task 1 tokens
- Produces:
  - `<AppCard :glow="bool" :hoverable="bool">`（默认 hoverable=true）
  - `<PageHeader title="..." subtitle="...">` + `#actions` 插槽
  - `<RouteProgress />`：自动监听 router 钩子，无需 props；App.vue 挂载后全局生效

- [ ] **Step 1: AppCard.vue**

```vue
<template>
  <div class="app-card" :class="{ glow, hoverable }">
    <slot />
  </div>
</template>

<script setup>
defineProps({
  glow: { type: Boolean, default: false },
  hoverable: { type: Boolean, default: true },
});
</script>

<style scoped>
.app-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.02), transparent 40%), var(--bg-surface);
  border: 1px solid var(--border); border-radius: var(--radius);
  padding: var(--card-padding); box-shadow: var(--shadow-sm);
  position: relative; transition: all 0.28s var(--ease-out);
}
.app-card::before {
  content: ""; position: absolute; top: 0; left: 14px; right: 14px; height: 1px;
  background: var(--gradient-aurora); opacity: 0; transition: opacity 0.3s var(--ease-out);
}
.app-card.hoverable:hover { border-color: var(--border-light); transform: translateY(-2px); box-shadow: var(--shadow); }
.app-card.hoverable:hover::before { opacity: 0.7; }
.app-card.glow { border-color: rgba(245, 168, 61, 0.3); box-shadow: var(--shadow-sm), 0 0 24px rgba(245, 168, 61, 0.08); }
.app-card.glow::before { opacity: 0.7; }
</style>
```

- [ ] **Step 2: PageHeader.vue**

```vue
<template>
  <header class="page-header-bar">
    <div class="page-header-copy">
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: "" },
});
</script>

<style scoped>
.page-header-bar { display: flex; align-items: flex-end; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.page-header-copy { flex: 1; min-width: 0; }
.page-header-copy h1 { font-family: var(--font-display); font-size: 26px; font-weight: 700; letter-spacing: -0.02em; }
.page-header-copy p { color: var(--text-muted); font-size: 13px; margin-top: 4px; }
.page-header-actions { display: flex; gap: 10px; align-items: center; }
</style>
```

- [ ] **Step 3: RouteProgress.vue**

```vue
<template>
  <div class="route-progress" :class="{ active: visible }" :style="{ width: width + '%' }"></div>
</template>

<script setup>
import { ref, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const visible = ref(false);
const width = ref(0);
let timer = null;

function start() {
  visible.value = true;
  width.value = 0;
  clearInterval(timer);
  timer = setInterval(() => {
    if (width.value < 82) width.value += Math.random() * 12;
  }, 120);
}

function done() {
  clearInterval(timer);
  width.value = 100;
  setTimeout(() => { visible.value = false; width.value = 0; }, 260);
}

const removeBefore = router.beforeEach((to, from, next) => { start(); next(); });
const removeAfter = router.afterEach(() => done());
const removeError = router.onError(() => done());

onBeforeUnmount(() => {
  removeBefore(); removeAfter(); removeError();
  clearInterval(timer);
});
</script>

<style scoped>
.route-progress {
  position: fixed; top: 0; left: 0; height: 2px; z-index: 2000;
  background: var(--gradient-aurora);
  box-shadow: 0 0 10px rgba(245, 168, 61, 0.55);
  opacity: 0; transition: width 0.18s ease-out, opacity 0.25s ease-out;
  pointer-events: none;
}
.route-progress.active { opacity: 1; }
</style>
```

- [ ] **Step 4: App.vue 挂载 RouteProgress**

模板顶部加 `<RouteProgress />`，script 中 import：

```vue
<template>
  <RouteProgress />
  <router-view v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
  <AppToast />
</template>

<script setup>
import AppToast from "./components/ui/AppToast.vue";
import RouteProgress from "./components/ui/RouteProgress.vue";
</script>
```

- [ ] **Step 5: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/components/ui/ pyclaw-web/src/App.vue
git commit -m "feat(web-ui): 新增 AppCard/PageHeader/RouteProgress 并全局挂载进度条"
```

---

### Task 5: WorkspaceLayout 重设计

**Files:**
- Modify: `pyclaw-web/src/views/WorkspaceLayout.vue`

**Interfaces:**
- Consumes: Task 1 tokens
- Produces: 工作台骨架新样式；导航结构、路由链接、`useAuth` 逻辑不变

- [ ] **Step 1: 模板微调**

`<script setup>` 完全不变。模板结构不变，仅做以下调整：
- `.brand-mark` 内容从 `▶` 改为 `◢`（或保留，样式升级为主）
- 每个 `.nav-item` 内最前面加一个 `<span class="nav-indicator"></span>`（激活指示条）
- 删除 `.workspace-chip` 区块（第 65-70 行），保留 `.account-row`

- [ ] **Step 2: 替换整个 `<style scoped>`**

```css
.workspace-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
}

.sidebar {
  position: sticky; top: 0; height: 100vh;
  display: flex; flex-direction: column;
  border-right: 1px solid var(--border);
  background: rgba(8, 11, 17, 0.78);
  backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px);
}

.brand {
  height: 60px; padding: 0 18px; display: flex; align-items: center; gap: 10px;
  color: var(--text-primary); text-decoration: none;
  font-family: var(--font-display); font-weight: 700; font-size: 17px; letter-spacing: -0.01em;
  border-bottom: 1px solid var(--border);
}
.brand.compact { height: auto; padding: 0; border: 0; }

.brand-mark {
  width: 28px; height: 28px; display: grid; place-items: center;
  border-radius: 9px; color: #0a0e14; background: var(--gradient-aurora);
  font-size: 12px; box-shadow: var(--glow-accent);
}

.side-nav { padding: 16px 10px; flex: 1; overflow-y: auto; }

.nav-section {
  margin: 18px 12px 8px; font-size: 11px; font-weight: 700;
  color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.6px;
}
.nav-section:first-child { margin-top: 0; }

.nav-item {
  position: relative;
  display: flex; align-items: center; gap: 10px;
  min-height: 36px; padding: 8px 12px; border-radius: 10px;
  color: var(--text-secondary); font-size: 13px; font-weight: 600; text-decoration: none;
  transition: background 0.18s var(--ease-out), color 0.18s var(--ease-out);
}
.nav-indicator {
  position: absolute; left: 0; top: 8px; bottom: 8px; width: 3px; border-radius: 3px;
  background: var(--gradient-aurora);
  opacity: 0; transform: scaleY(0.4);
  transition: opacity 0.2s var(--ease-out), transform 0.25s var(--ease-spring);
}
.nav-item:hover { color: var(--text-primary); background: rgba(255, 255, 255, 0.04); }
.nav-item.router-link-active {
  color: var(--accent);
  background: linear-gradient(90deg, var(--accent-glow), transparent 80%);
}
.nav-item.router-link-active .nav-indicator { opacity: 1; transform: scaleY(1); }

.nav-icon { width: 16px; color: currentColor; opacity: 0.85; text-align: center; }

.sidebar-footer { padding: 14px; border-top: 1px solid var(--border); }

.account-row { display: flex; align-items: center; gap: 10px; }
.avatar {
  width: 30px; height: 30px; display: grid; place-items: center; border-radius: 50%;
  background: var(--gradient-aurora); color: #0a0e14; font-size: 12px; font-weight: 800;
}
.account-meta { min-width: 0; flex: 1; display: grid; line-height: 1.3; }
.account-meta strong {
  overflow: hidden; color: var(--text-primary); font-size: 12px;
  text-overflow: ellipsis; white-space: nowrap;
}
.account-meta span { color: var(--text-muted); font-size: 11px; }

.logout-icon {
  width: 28px; height: 28px; border: 1px solid transparent; border-radius: 8px;
  color: var(--text-muted); background: transparent; transition: all 0.18s var(--ease-out);
}
.logout-icon:hover { color: var(--danger); border-color: rgba(255, 92, 92, 0.45); background: rgba(255, 92, 92, 0.06); }

.workspace-main { min-width: 0; }
.mobile-topbar { display: none; }

.main-content {
  max-width: min(1180px, calc(100vw - 232px));
  margin: 0 auto;
  padding: 34px 42px 72px;
}

.btn-logout {
  padding: 5px 14px; font-size: 12px; color: var(--text-muted);
  background: transparent; border: 1px solid var(--border); border-radius: var(--radius-sm);
}
.btn-logout:hover { color: var(--danger); border-color: var(--danger); background: rgba(255, 92, 92, 0.06); }

.page-enter-active { transition: opacity 0.2s var(--ease-out), transform 0.2s var(--ease-out); }
.page-leave-active { transition: opacity 0.1s var(--ease-out); }
.page-enter-from { opacity: 0; transform: translateY(6px); }
.page-leave-to { opacity: 0; }

@media (max-width: 860px) {
  .workspace-shell { display: block; }
  .sidebar { display: none; }
  .mobile-topbar {
    position: sticky; top: 0; z-index: 50; height: 56px; padding: 0 18px;
    display: flex; align-items: center; justify-content: space-between;
    border-bottom: 1px solid var(--border);
    background: rgba(8, 11, 17, 0.85);
    backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  }
  .main-content { padding: 24px 18px 48px; }
}
```

- [ ] **Step 3: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功；侧边栏激活项有左侧渐变指示条。

```bash
git add pyclaw-web/src/views/WorkspaceLayout.vue
git commit -m "feat(web-ui): 重设计工作台骨架（毛玻璃侧栏 + 渐变激活指示条）"
```

---

### Task 6: WelcomePage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/WelcomePage.vue`

**Interfaces:**
- Consumes: Task 1 tokens、Task 2 AppButton
- Produces: 门户首页新视觉；`handleStart` 逻辑不变

- [ ] **Step 1: 重写模板（script 不变）**

```vue
<template>
  <div class="welcome">
    <div class="aurora aurora-1"></div>
    <div class="aurora aurora-2"></div>
    <div class="welcome-content">
      <div class="logo-badge">
        <span class="logo-mark">◢</span>
      </div>
      <h1 class="brand-title">PyClaw</h1>
      <p class="subtitle">Multi-Agent Workspace — 构建、管理、运行你的 AI Agent</p>
      <div class="features">
        <div class="feature">
          <span class="feature-icon">🤖</span>
          <span>创建自定义 Agent</span>
        </div>
        <div class="feature">
          <span class="feature-icon">🔧</span>
          <span>灵活配置工具与模型</span>
        </div>
        <div class="feature">
          <span class="feature-icon">💬</span>
          <span>飞书 & 多渠道接入</span>
        </div>
      </div>
      <AppButton variant="primary" class="btn-start" :loading="starting" loading-text="检查登录态..." @click="handleStart">
        开启我的 PyClaw 之旅
      </AppButton>
      <p class="login-link">
        已有账号？<router-link to="/login">立即登录</router-link>
      </p>
    </div>
  </div>
</template>
```

script 改为（仅新增 `starting` ref 与 AppButton import，`checkAuth` 逻辑不变）：

```js
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../composables/useAuth.js";
import AppButton from "../components/ui/AppButton.vue";

const router = useRouter();
const { checkAuth } = useAuth();
const starting = ref(false);

async function handleStart() {
  if (starting.value) return;
  starting.value = true;
  try {
    const loggedIn = await checkAuth();
    router.push(loggedIn ? "/workspace" : "/login");
  } finally {
    starting.value = false;
  }
}
```

- [ ] **Step 2: 替换 `<style scoped>`**

```css
.welcome {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  position: relative; overflow: hidden;
}
.aurora {
  position: absolute; border-radius: 50%; filter: blur(90px); pointer-events: none;
}
.aurora-1 {
  width: 480px; height: 480px; top: -120px; right: -80px;
  background: radial-gradient(circle, rgba(245, 168, 61, 0.16), transparent 65%);
  animation: drift 14s var(--ease-in-out) infinite alternate;
}
.aurora-2 {
  width: 420px; height: 420px; bottom: -100px; left: -60px;
  background: radial-gradient(circle, rgba(139, 124, 246, 0.14), transparent 65%);
  animation: drift 18s var(--ease-in-out) infinite alternate-reverse;
}
@keyframes drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(-40px, 30px) scale(1.12); }
}
.welcome-content { text-align: center; max-width: 560px; padding: 48px 32px; position: relative; }
.logo-badge {
  width: 72px; height: 72px; margin: 0 auto 28px;
  display: grid; place-items: center; border-radius: 20px;
  background: var(--gradient-aurora); box-shadow: var(--glow-accent);
}
.logo-mark { font-size: 30px; color: #0a0e14; }
.brand-title {
  font-family: var(--font-display); font-size: 56px; font-weight: 700; letter-spacing: -0.03em;
  margin-bottom: 14px;
  background: var(--gradient-aurora);
  -webkit-background-clip: text; background-clip: text;
  -webkit-text-fill-color: transparent; color: transparent;
}
.subtitle { color: var(--text-secondary); font-size: 17px; margin-bottom: 44px; }
.features { display: flex; justify-content: center; gap: 14px; margin-bottom: 44px; flex-wrap: wrap; }
.feature {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border);
  color: var(--text-secondary); font-size: 13px;
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  transition: all 0.25s var(--ease-out);
}
.feature:hover { border-color: var(--border-light); color: var(--text-primary); transform: translateY(-2px); box-shadow: var(--shadow-sm); }
.feature-icon { font-size: 16px; }
.btn-start { padding: 14px 42px; font-size: 16px; border-radius: 12px; }
.login-link { margin-top: 26px; color: var(--text-muted); font-size: 14px; }
@media (prefers-reduced-motion: reduce) {
  .aurora-1, .aurora-2 { animation: none; }
}
```

- [ ] **Step 3: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/WelcomePage.vue
git commit -m "feat(web-ui): 重设计欢迎页（极光光晕 + 渐变品牌字 + 玻璃特性标签）"
```

---

### Task 7: LoginPage / RegisterPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/LoginPage.vue`
- Modify: `pyclaw-web/src/views/RegisterPage.vue`

**Interfaces:**
- Consumes: AppButton、useToast
- Produces: 登录/注册新视觉；提交逻辑、字段、跳转不变

- [ ] **Step 1: 两页统一改为「极光背景 + 居中玻璃卡片」结构**

模板结构（以 LoginPage 为例，RegisterPage 同构，字段保持原有 username/password/confirm 等不变）：

```vue
<template>
  <div class="auth-page">
    <div class="aurora aurora-1"></div>
    <div class="aurora aurora-2"></div>
    <div class="auth-card">
      <div class="auth-brand">
        <span class="brand-mark">◢</span>
        <span class="brand-name">PyClaw</span>
      </div>
      <h1>欢迎回来</h1>
      <p class="auth-sub">登录你的 Multi-Agent 工作区</p>
      <form @submit.prevent="handleLogin">
        <!-- 保留原有全部 form-group 字段与 v-model，仅替换提交按钮 -->
        <AppButton variant="primary" type="submit" class="auth-submit" :loading="submitting" loading-text="登录中...">
          登录
        </AppButton>
      </form>
      <p class="auth-switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </div>
  </div>
</template>
```

- [ ] **Step 2: script 调整**

- 新增 `const submitting = ref(false)`，提交函数开头 `if (submitting.value) return; submitting.value = true;`，`finally { submitting.value = false; }`
- 原有页面内错误文本（`error` ref 渲染的 `.error-msg`）改为 `toast.error(e.message || "登录失败")`；import `useToast`
- 其余逻辑（api 调用、localStorage、router.push）不变

- [ ] **Step 3: 两页共用样式（各自 scoped 内相同）**

```css
.auth-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  position: relative; overflow: hidden; padding: 24px;
}
.aurora { position: absolute; border-radius: 50%; filter: blur(90px); pointer-events: none; }
.aurora-1 {
  width: 460px; height: 460px; top: -100px; left: -80px;
  background: radial-gradient(circle, rgba(245, 168, 61, 0.14), transparent 65%);
  animation: drift 15s var(--ease-in-out) infinite alternate;
}
.aurora-2 {
  width: 400px; height: 400px; bottom: -90px; right: -60px;
  background: radial-gradient(circle, rgba(77, 208, 225, 0.1), transparent 65%);
  animation: drift 19s var(--ease-in-out) infinite alternate-reverse;
}
@keyframes drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(36px, -28px) scale(1.1); }
}
.auth-card {
  width: 400px; max-width: 100%; padding: 40px 36px; position: relative;
  background: rgba(16, 21, 31, 0.72); border: 1px solid var(--border-light);
  border-radius: var(--radius-lg); box-shadow: var(--shadow);
  backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
  animation: card-in 0.4s var(--ease-out);
}
@keyframes card-in {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
.auth-brand { display: flex; align-items: center; gap: 10px; margin-bottom: 28px; }
.brand-mark {
  width: 30px; height: 30px; display: grid; place-items: center;
  border-radius: 9px; background: var(--gradient-aurora); color: #0a0e14;
  font-size: 13px; box-shadow: var(--glow-accent);
}
.brand-name { font-family: var(--font-display); font-weight: 700; font-size: 16px; }
.auth-card h1 { font-family: var(--font-display); font-size: 24px; font-weight: 700; letter-spacing: -0.02em; margin-bottom: 6px; }
.auth-sub { color: var(--text-muted); font-size: 13px; margin-bottom: 26px; }
.auth-submit { width: 100%; margin-top: 8px; padding: 12px; }
.auth-switch { margin-top: 22px; text-align: center; color: var(--text-muted); font-size: 13px; }
@media (prefers-reduced-motion: reduce) {
  .aurora-1, .aurora-2 { animation: none; }
  .auth-card { animation: none; }
}
```

- [ ] **Step 4: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功；登录/注册提交时按钮进入 loading 态，失败弹 Toast。

```bash
git add pyclaw-web/src/views/LoginPage.vue pyclaw-web/src/views/RegisterPage.vue
git commit -m "feat(web-ui): 重设计登录/注册页（玻璃拟态卡片 + 提交 loading + Toast 错误提示）"
```

---

### Task 8: ClawListPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/ClawListPage.vue`

**Interfaces:**
- Consumes: AppButton、AppSkeleton、AppEmpty、AppTag、PageHeader、useToast
- Produces: Claw 列表新视觉 + 完整加载态；数据加载、创建、添加角色、删除逻辑不变

- [ ] **Step 1: 模板改造要点**

- 页头改用 `<PageHeader title="Claw 管理" subtitle="每个 Claw 是一个独立工作区，包含多个 Agent 角色。">`，`#actions` 放「新建 Claw」AppButton
- 删除 `.breadcrumb` 行
- 首屏 loading（`loading` ref 为 true 时）改为骨架屏：

```vue
<div v-if="loading" class="claw-skeletons">
  <div class="stat-row">
    <AppSkeleton v-for="i in 4" :key="i" variant="rect" :height="86" />
  </div>
  <div class="claw-grid">
    <AppSkeleton v-for="i in 3" :key="i" variant="rect" :height="260" />
  </div>
</div>
```

- 错误态保留 `error` 展示，追加「重试」AppButton（调用原 `load()`）
- `.metric-grid` 四张统计卡保留，class 改为 `stat-card`（复用全局样式），数值加 `stat-value`、标签加 `stat-label`
- `.claw-card` 保留结构与 stagger `transitionDelay`，状态 `.status-pill` 换成 `<AppTag :tone="..." :pulse="status === 'active'">`
- 空状态改用 `<AppEmpty icon="＋" title="还没有 Claw" description="...">`，操作按钮为 AppButton
- 两个弹窗（新建 / 添加角色）改用 `<AppModal>`，提交按钮用 AppButton + `submitting` loading
- 删除 Claw / 删除角色：成功后 `toast.success("已删除")`，失败 `toast.error(e.message)`

- [ ] **Step 2: script 调整**

- 新增 `const submitting = ref(false)`，`handleCreate` / `handleAddRole` 包裹 loading（同 Task 7 模式）
- 新增 `const deletingId = ref(null)`，删除按钮 loading 态按行区分
- import 各组件与 `useToast`；其余逻辑（`load`、`statusClass`、`goDetail`、`openCreate` 等）不变

- [ ] **Step 3: 样式要点（scoped 重写）**

- `.claw-grid`：`display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 18px;`
- `.claw-card`：复用 AppCard 视觉（渐变顶条 hover 亮起、上浮发光），cursor: pointer
- `.metric-card` → 全局 `.stat-card`；飞书卡数值色用 `var(--accent-2)`，Agent 卡用 `var(--accent-3)`
- `.guide-band`：玻璃质感横条（`backdrop-filter: blur` + 渐变左边框）
- 角色列表 `.role-card`：圆角 10px、hover 背景亮起

- [ ] **Step 4: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功；首屏骨架屏、卡片 stagger 入场、创建/删除按钮 loading 均生效。

```bash
git add pyclaw-web/src/views/ClawListPage.vue
git commit -m "feat(web-ui): 重设计 Claw 列表页（骨架屏 + 发光卡片 + 操作 loading）"
```

---

### Task 9: ClawDetailPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/ClawDetailPage.vue`

**Interfaces:**
- Consumes: AppButton、AppSkeleton、AppTag、PageHeader、AppModal、useToast
- Produces: 详情页新视觉；数据与操作逻辑不变

- [ ] **Step 1: 改造要点**

- 页头：`<PageHeader :title="claw?.name || 'Claw 详情'">`，`#actions` 集中放操作按钮（进入对话、工作区文件、编辑、删除等，保持原有按钮与行为）
- 首屏 loading 改骨架屏（标题条 + 2 张信息卡）
- 信息分区卡片化：基本信息、角色列表、飞书配置等各为一张 `.card` / AppCard
- 状态标签换 `<AppTag>`，运行中 `pulse`
- 所有提交类按钮（保存、删除确认）加 loading
- 错误/成功反馈改 Toast

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/ClawDetailPage.vue
git commit -m "feat(web-ui): 重设计 Claw 详情页（分区卡片 + 页头操作组 + 骨架屏）"
```

---

### Task 10: ClawChatPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/ClawChatPage.vue`

**Interfaces:**
- Consumes: AppTag、AppSpinner、useToast
- Produces: 对话页新视觉；消息收发、会话切换、工具审批逻辑全部不变

- [ ] **Step 1: 改造要点**

- 整体布局不变（会话侧栏 + 消息区 + 输入区），视觉升级：
  - 会话侧栏：毛玻璃底、激活会话项渐变左指示条（同 WorkspaceLayout 导航）
  - 消息气泡：用户右侧琥珀渐变底；AI 左侧 `var(--bg-raised)` 底 + 1px 渐变描边（`border-image` 或伪元素）
  - 「打字中」三点动画保留并改为琥珀色呼吸
  - 输入区：悬浮底栏（圆角 16px、毛玻璃、focus 时边框发光）
  - 发送按钮：圆形渐变按钮，sending 时显示 AppSpinner
- 工具审批弹窗：升级为 AppModal 风格（保留原有字段：工具名称/意图/参数摘要/过期时间/拒绝原因），风险等级用 `<AppTag :tone="risk === 'high' ? 'danger' : 'warning'">`，同意/拒绝按钮加 `resolvingApproval` loading（已有 ref，直接绑定）
- 会话消息加载（`selectSession`）加局部骨架屏：新增 `loadingMessages` ref
- 错误提示 `.chat-error` 改 Toast

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功；发送消息、审批同意/拒绝均有 loading 表现。

```bash
git add pyclaw-web/src/views/ClawChatPage.vue
git commit -m "feat(web-ui): 重设计对话页（渐变气泡 + 悬浮输入栏 + 审批弹窗升级）"
```

---

### Task 11: WorkspaceFilesPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/WorkspaceFilesPage.vue`

**Interfaces:**
- Consumes: AppSkeleton、AppEmpty、PageHeader、useToast
- Produces: 文件页新视觉；文件加载/下载逻辑不变

- [ ] **Step 1: 改造要点**

- PageHeader 统一页头（标题「工作区文件」+ 返回按钮放 actions）
- 文件列表改 `.data-table`（名称 / 大小 / 修改时间 / 操作），文件名前加类型图标（📄/📁/🖼 按扩展名）
- loading 改表格骨架（5 行 `<AppSkeleton variant="text">`）
- 空状态用 AppEmpty
- 操作反馈改 Toast

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/WorkspaceFilesPage.vue
git commit -m "feat(web-ui): 重设计工作区文件页（表格化 + 骨架屏）"
```

---

### Task 12: AgentConfigPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/AgentConfigPage.vue`

**Interfaces:**
- Consumes: AppButton、AppSkeleton、AppModal、AppEmpty、useToast
- Produces: Agent 配置页新视觉；增删改查逻辑不变

- [ ] **Step 1: 改造要点**

- PageHeader + 「新建 Agent」AppButton
- 列表卡片化（名称 + agentKey 等宽字体 + 描述），stagger 入场
- 首屏骨架 = 卡片列表骨架
- 编辑/新建弹窗改 AppModal，保存按钮 loading
- 删除确认与结果反馈改 Toast

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/AgentConfigPage.vue
git commit -m "feat(web-ui): 重设计 Agent 配置页（卡片列表 + 弹窗 loading）"
```

---

### Task 13: ProviderPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/ProviderPage.vue`

**Interfaces:**
- Consumes: AppButton、AppSkeleton、AppModal、AppTag、useToast
- Produces: Provider 页新视觉；逻辑不变

- [ ] **Step 1: 改造要点**

- PageHeader + 新建按钮
- Provider 卡片网格：名称、类型 AppTag、模型列表等宽字体
- 首屏骨架；新建/编辑 AppModal + 保存 loading；反馈 Toast

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/ProviderPage.vue
git commit -m "feat(web-ui): 重设计 Provider 管理页"
```

---

### Task 14: SecretPage / TokenPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/SecretPage.vue`
- Modify: `pyclaw-web/src/views/TokenPage.vue`

**Interfaces:**
- Consumes: AppButton、AppSkeleton、AppModal、useToast
- Produces: 两页新视觉；逻辑不变

- [ ] **Step 1: 改造要点（两页同构）**

- PageHeader + 新建按钮
- 列表改 `.data-table` 或卡片；敏感值（Token/Secret）用 `var(--font-mono)` 等宽字体 + 「复制」小按钮（`navigator.clipboard.writeText`，成功 `toast.success("已复制")`；若原页面已有复制逻辑则保留原逻辑仅换样式）
- 首屏骨架；新建弹窗 AppModal + loading；反馈 Toast

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/SecretPage.vue pyclaw-web/src/views/TokenPage.vue
git commit -m "feat(web-ui): 重设计 Secret/Token 管理页（等宽敏感值 + 一键复制）"
```

---

### Task 15: ToolCatalogPage / PodStatusPage 重设计

**Files:**
- Modify: `pyclaw-web/src/views/ToolCatalogPage.vue`
- Modify: `pyclaw-web/src/views/PodStatusPage.vue`

**Interfaces:**
- Consumes: AppSkeleton、AppTag、AppEmpty、PageHeader
- Produces: 两页新视觉；逻辑不变

- [ ] **Step 1: ToolCatalogPage 要点**

- PageHeader（标题「工具目录」）
- 工具卡片网格（`repeat(auto-fill, minmax(280px, 1fr))`），风险等级用 AppTag（high→danger / medium→warning / low→success，按原数据字段映射）
- 首屏骨架 = 卡片网格骨架

- [ ] **Step 2: PodStatusPage 要点**

- 顶部 `.stat-row` 统计（总数 / 运行中 / 异常，沿用原数据计算）
- Pod 卡片：名称 + `<AppTag tone="success" :pulse="true">运行中</AppTag>` 等状态映射
- 首屏骨架；空状态 AppEmpty

- [ ] **Step 3: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/ToolCatalogPage.vue pyclaw-web/src/views/PodStatusPage.vue
git commit -m "feat(web-ui): 重设计工具目录与 Pod 状态页（荧光标签 + 状态呼吸灯）"
```

---

### Task 16: 管理后台四页重设计

**Files:**
- Modify: `pyclaw-web/src/views/admin/UserManagePage.vue`
- Modify: `pyclaw-web/src/views/admin/ChannelPage.vue`
- Modify: `pyclaw-web/src/views/admin/AuditLogPage.vue`
- Modify: `pyclaw-web/src/views/admin/UsagePage.vue`

**Interfaces:**
- Consumes: AppButton、AppSkeleton、AppModal、AppTag、PageHeader、useToast
- Produces: 四页新视觉；逻辑不变

- [ ] **Step 1: 改造要点**

- 四页统一 PageHeader
- 表格页（用户/渠道/审计）：`.data-table` 全局样式已升级，表头加 `position: sticky; top: 0`（页面 scoped 内），状态列换 AppTag；首屏表格骨架
- 操作按钮（编辑/禁用/重置密码等）保持行为，加 loading；反馈 Toast
- UsagePage：统计卡片用 `.stat-row` + `.stat-card`；若有用量明细，加纯 CSS 柱状图（`div` 高度按比例，琥珀→紫渐变填充）

- [ ] **Step 2: 构建验证 + Commit**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功。

```bash
git add pyclaw-web/src/views/admin/
git commit -m "feat(web-ui): 重设计管理后台四页（表格升级 + 统计卡片）"
```

---

### Task 17: 全量验证与走查

**Files:**
- 不新增/修改源码（仅发现问题时修复）

- [ ] **Step 1: 全量构建**

Run: `cd pyclaw-web && npm run build`
Expected: 构建成功，无警告（CSS 相关）。

- [ ] **Step 2: 功能不变性走查清单（人工，逐页核对）**

| 页面 | 核对点 |
|---|---|
| Welcome | 「开启之旅」按登录态跳转 /workspace 或 /login |
| Login/Register | 登录注册成功跳转、失败提示 |
| ClawList | 列表加载、新建、添加角色、删除 Claw、删除角色 |
| ClawDetail | 详情展示、各操作按钮 |
| ClawChat | 发消息、切会话、新建会话、工具审批同意/拒绝（含拒绝理由） |
| WorkspaceFiles | 文件列表展示与操作 |
| AgentConfig | 增删改查 |
| Provider | 增删改查 |
| Secret/Token | 增删查、复制 |
| ToolCatalog | 目录展示 |
| PodStatus | 状态展示 |
| Admin×4 | 用户/渠道/审计/用量展示与操作 |
| 权限 | 无权限路由仍跳转 /workspace；未登录跳 /login |

- [ ] **Step 3: 加载态走查**

- 路由切换出现顶部进度条
- 每个首屏加载有骨架屏
- 每个提交按钮点击后有 loading 且不可重复点击

- [ ] **Step 4: 动效降级检查**

- 开 `prefers-reduced-motion` 模拟（DevTools Rendering 面板），确认动效基本静止

- [ ] **Step 5: 最终 Commit（如有修复）**

```bash
git add -A
git commit -m "fix(web-ui): 全量走查修复"
```

---

## Self-Review 记录

- **Spec 覆盖**：tokens（T1）、共享组件（T2-T4）、三层加载（T2/T3/T4 + 各页任务）、动效（T1 全局 + 各页）、18 页面（T5-T16）、验证（T17）——全覆盖。
- **类型一致性**：组件 props 名（`variant`/`loading`/`tone`/`pulse`/`glow`/`hoverable`）在 T2-T4 定义，后续任务引用一致；`useToast` 导出 `toast.success/error/info` 一致。
- **占位符**：无 TBD/TODO；页面任务给出结构代码与明确改造点，script 逻辑明确标注「不变」。
