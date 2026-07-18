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
