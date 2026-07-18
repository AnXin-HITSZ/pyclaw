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
