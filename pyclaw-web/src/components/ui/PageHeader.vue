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
