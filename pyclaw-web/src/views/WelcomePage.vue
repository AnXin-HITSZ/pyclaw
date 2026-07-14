<template>
  <div class="welcome">
    <div class="welcome-content">
      <div class="logo">
        <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
          <rect width="64" height="64" rx="16" fill="#58a6ff" />
          <path d="M20 44V20l12 8-12 8z" fill="#0d1117" />
          <path d="M32 36l12-8-12-8v16z" fill="#0d1117" opacity="0.7" />
          <circle cx="44" cy="28" r="4" fill="#0d1117" opacity="0.5" />
        </svg>
      </div>
      <h1>PyClaw</h1>
      <p class="subtitle">Multi-Agent Workspace — 构建、管理、运行你的 AI Agent</p>
      <div class="features">
        <div class="feature">
          <span class="feature-icon">&#x1F916;</span>
          <span>创建自定义 Agent</span>
        </div>
        <div class="feature">
          <span class="feature-icon">&#x1F527;</span>
          <span>灵活配置工具与模型</span>
        </div>
        <div class="feature">
          <span class="feature-icon">&#x1F4AC;</span>
          <span>飞书 & 多渠道接入</span>
        </div>
      </div>
      <button class="btn-start" @click="handleStart">开启我的 PyClaw 之旅</button>
      <p class="login-link">
        已有账号？<router-link to="/login">立即登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";
import { useAuth } from "../composables/useAuth.js";

const router = useRouter();
const { checkAuth } = useAuth();

async function handleStart() {
  const loggedIn = await checkAuth();
  if (loggedIn) {
    router.push("/workspace");
  } else {
    router.push("/login");
  }
}
</script>

<style scoped>
.welcome {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.welcome-content {
  text-align: center;
  max-width: 520px;
  padding: 48px 32px;
}

.logo {
  margin-bottom: 24px;
}

h1 {
  font-size: 48px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: -1px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 18px;
  margin-bottom: 40px;
}

.features {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-bottom: 40px;
  flex-wrap: wrap;
}

.feature {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}

.feature-icon {
  font-size: 20px;
}

.btn-start {
  display: inline-block;
  padding: 14px 40px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  border: none;
  border-radius: 8px;
  transition: background 0.2s;
}

.btn-start:hover {
  background: var(--accent-hover);
}

.login-link {
  margin-top: 24px;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
