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

<script setup>
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
</script>

<style scoped>
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
</style>
