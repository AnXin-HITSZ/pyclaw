<template>
  <router-view v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup>
</script>

<style>
/* ── Design Tokens ── */
:root {
  --bg-deep: #0a0e14;
  --bg-surface: #11161d;
  --bg-raised: #191f2a;
  --bg-hover: #1e2530;
  --border: #242b36;
  --border-light: #2c3340;
  --text-primary: #dde2e8;
  --text-secondary: #8896a7;
  --text-muted: #5c6878;
  --accent: #f0a33a;
  --accent-soft: #c78a2e;
  --accent-glow: rgba(240, 163, 58, 0.12);
  --accent-glow-strong: rgba(240, 163, 58, 0.22);
  --success: #3fb950;
  --danger: #f85149;
  --warning: #d29922;
  --radius-sm: 6px;
  --radius: 10px;
  --radius-lg: 14px;
  --shadow-sm: 0 1px 3px rgba(0,0,0,0.3);
  --shadow: 0 4px 12px rgba(0,0,0,0.4);
  --shadow-glow: 0 0 20px var(--accent-glow);
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
  --ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
  --topbar-height: 56px;
  --content-max-width: 1100px;
  --content-gutter: 48px;
  --card-padding: 24px;
  --section-gap: 24px;
  /* Backward-compatible aliases */
  --bg-primary: var(--bg-deep);
  --bg-secondary: var(--bg-surface);
  --bg-tertiary: var(--bg-raised);
  --border-color: var(--border);
  --accent-hover: var(--accent-soft);
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
  background-color: var(--bg-deep);
  color: var(--text-primary);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

a { color: var(--accent); text-decoration: none; transition: color 0.2s var(--ease-out); }
a:hover { color: var(--accent-soft); }
button { cursor: pointer; font-family: inherit; }
input, textarea, select { font-family: inherit; }

/* ── Scrollbar ── */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: var(--border-light); }

/* ── Page transition ── */
.page-enter-active { transition: opacity 0.25s var(--ease-out), transform 0.25s var(--ease-out); }
.page-leave-active { transition: opacity 0.15s var(--ease-out), transform 0.15s var(--ease-out); }
.page-enter-from { opacity: 0; transform: translateY(8px); }
.page-leave-to { opacity: 0; transform: translateY(-4px); }

/* ── Staggered card enter ── */
.stagger-enter-active { transition: opacity 0.4s var(--ease-out), transform 0.4s var(--ease-out); }
.stagger-enter-from { opacity: 0; transform: translateY(16px); }

/* ── Shared base styles ── */
.page { max-width: 1000px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.page-header h1 { font-size: 22px; font-weight: 700; letter-spacing: -0.3px; flex: 1; }

.btn-primary {
  padding: 8px 20px; font-size: 14px; font-weight: 600; color: #0a0e14;
  background: var(--accent); border: none; border-radius: var(--radius-sm);
  transition: all 0.2s var(--ease-out);
  position: relative; overflow: hidden;
}
.btn-primary::after {
  content: ""; position: absolute; inset: 0;
  background: linear-gradient(135deg, transparent 40%, rgba(255,255,255,0.1) 50%, transparent 60%);
  transform: translateX(-100%); transition: transform 0.4s var(--ease-out);
}
.btn-primary:hover::after { transform: translateX(100%); }
.btn-primary:hover { background: var(--accent-soft); transform: translateY(-1px); box-shadow: var(--shadow-glow); }
.btn-primary:active { transform: translateY(0); }
.btn-primary:disabled { opacity: 0.4; pointer-events: none; }

.btn-secondary {
  padding: 8px 20px; font-size: 14px; color: var(--text-secondary);
  background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius-sm);
  transition: all 0.2s var(--ease-out);
}
.btn-secondary:hover { background: var(--bg-raised); border-color: var(--border-light); color: var(--text-primary); }

.btn-back {
  padding: 6px 14px; font-size: 13px; color: var(--text-muted);
  background: transparent; border: 1px solid var(--border); border-radius: var(--radius-sm);
  transition: all 0.2s var(--ease-out);
}
.btn-back:hover { color: var(--text-secondary); border-color: var(--border-light); background: var(--bg-surface); }

.card {
  background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius);
  padding: var(--card-padding); transition: all 0.25s var(--ease-out);
  position: relative; box-shadow: var(--shadow-sm);
}
.card::before {
  content: ""; position: absolute; top: 0; left: 12px; right: 12px; height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0; transition: opacity 0.3s var(--ease-out);
}
.card:hover { border-color: var(--border-light); transform: translateY(-2px); box-shadow: var(--shadow); }
.card:hover::before { opacity: 0.6; }

.card h3 { font-size: 15px; font-weight: 600; margin-bottom: 16px; letter-spacing: -0.2px; }

.status-tag { font-size: 11px; padding: 2px 10px; border-radius: 10px; font-weight: 500; }
.status-tag.active { background: rgba(63,185,80,0.12); color: var(--success); }
.status-tag.inactive { background: rgba(248,81,73,0.1); color: var(--danger); }

.loading, .no-data { text-align: center; padding: 48px; color: var(--text-muted); font-size: 14px; }
.error-msg { text-align: center; padding: 48px; color: var(--danger); font-size: 14px; }

.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 13px; color: var(--text-secondary); font-weight: 500; }
.form-group input, .form-group textarea, .form-group select {
  width: 100%; padding: 10px 14px; background: var(--bg-deep); border: 1px solid var(--border);
  border-radius: var(--radius-sm); color: var(--text-primary); font-size: 14px;
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}
.form-group input:focus, .form-group textarea:focus, .form-group select:focus {
  outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow);
}

.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.7); display: flex;
  align-items: center; justify-content: center; z-index: 100;
  backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px);
}
.modal {
  background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: 32px; width: 480px; max-width: 90vw;
  animation: modal-enter 0.25s var(--ease-spring);
}
.modal h2 { margin-bottom: 20px; font-weight: 700; letter-spacing: -0.3px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }

@keyframes modal-enter {
  from { opacity: 0; transform: scale(0.95) translateY(10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

/* ── Skeleton pulse ── */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.skeleton { animation: pulse 1.5s var(--ease-in-out) infinite; background: var(--bg-raised); border-radius: var(--radius-sm); }

/* ── Table base ── */
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th {
  text-align: left; padding: 10px 14px; background: var(--bg-surface);
  color: var(--text-muted); font-weight: 600; font-size: 11px;
  text-transform: uppercase; letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border);
}
.data-table td {
  padding: 10px 14px; border-bottom: 1px solid var(--border);
  color: var(--text-primary);
}
.data-table tbody tr { transition: background 0.15s var(--ease-out); }
.data-table tbody tr:hover { background: var(--bg-hover); }
.data-table tbody tr:nth-child(even) { background: rgba(255,255,255,0.015); }
.data-table tbody tr:nth-child(even):hover { background: var(--bg-hover); }

/* ── Empty state ── */
.empty-state { text-align: center; padding: 64px 24px; }
.empty-state-icon { font-size: 44px; margin-bottom: 16px; opacity: 0.5; }
.empty-state h3 { font-size: 17px; font-weight: 600; margin-bottom: 6px; color: var(--text-secondary); }
.empty-state p { font-size: 14px; color: var(--text-muted); max-width: 420px; margin: 0 auto 20px; line-height: 1.6; }

/* ── Stat cards row ── */
.stat-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: var(--section-gap); }
.stat-card {
  background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 20px 24px; box-shadow: var(--shadow-sm);
  transition: all 0.2s var(--ease-out);
}
.stat-card:hover { border-color: var(--border-light); box-shadow: var(--shadow); }
.stat-value { font-size: 28px; font-weight: 800; letter-spacing: -0.5px; line-height: 1.1; }
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
</style>
