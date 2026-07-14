<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">
        <svg width="40" height="40" viewBox="0 0 64 64" fill="none">
          <rect width="64" height="64" rx="16" fill="url(#auth-grad)" />
          <path d="M18 46V18l14 10-14 10z" fill="#0a0e14" />
          <path d="M32 38l14-10-14-10v20z" fill="#0a0e14" opacity="0.7" />
          <defs>
            <linearGradient id="auth-grad" x1="0" y1="0" x2="64" y2="64">
              <stop offset="0%" stop-color="#f0a33a" />
              <stop offset="100%" stop-color="#c78a2e" />
            </linearGradient>
          </defs>
        </svg>
      </div>
      <h2>登录 PyClaw</h2>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">用户名</label>
          <input id="username" v-model="username" type="text" required placeholder="请输入用户名" autocomplete="username" />
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <input id="password" v-model="password" type="password" required placeholder="请输入密码" autocomplete="current-password" />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="btn-auth" :disabled="loading">
          <span v-if="!loading">登录</span>
          <span v-else class="sending-spinner"></span>
        </button>
      </form>
      <p class="switch-link">
        没有账号？<router-link to="/register">立即注册</router-link>
      </p>
      <p class="back-link">
        <router-link to="/">← 返回首页</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../composables/useAuth.js";

const router = useRouter();
const { login, loading } = useAuth();

const username = ref("");
const password = ref("");
const error = ref("");

async function handleLogin() {
  error.value = "";
  try {
    await login(username.value, password.value);
    router.push("/workspace");
  } catch (e) {
    error.value = e.message || "登录失败，请检查用户名和密码";
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: radial-gradient(ellipse at 50% 0%, rgba(240,163,58,0.06) 0%, transparent 60%),
              var(--bg-deep);
}

.auth-card {
  background: var(--bg-surface); border: 1px solid var(--border);
  border-radius: var(--radius-lg); padding: 44px 40px; width: 400px; max-width: 90vw;
  animation: modal-enter 0.35s var(--ease-spring);
}

.auth-logo { text-align: center; margin-bottom: 20px; }
.auth-logo svg { transition: transform 0.3s var(--ease-spring); }
.auth-logo svg:hover { transform: rotate(-5deg) scale(1.05); }

h2 { text-align: center; margin-bottom: 32px; font-size: 22px; font-weight: 700; letter-spacing: -0.3px; }

.form-group { margin-bottom: 20px; }
label { display: block; margin-bottom: 6px; font-size: 13px; font-weight: 500; color: var(--text-secondary); }
input {
  width: 100%; padding: 10px 14px; background: var(--bg-deep); border: 1px solid var(--border);
  border-radius: var(--radius-sm); color: var(--text-primary); font-size: 14px;
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}
input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }

.error { color: var(--danger); font-size: 13px; margin-bottom: 16px; animation: fade-in-up 0.2s var(--ease-out); }
@keyframes fade-in-up { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
@keyframes modal-enter { from { opacity: 0; transform: scale(0.96) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }

.btn-auth {
  width: 100%; padding: 12px; font-size: 15px; font-weight: 700; color: #0a0e14;
  background: var(--accent); border: none; border-radius: var(--radius-sm);
  transition: all 0.2s var(--ease-out);
  display: flex; align-items: center; justify-content: center; min-height: 45px;
}
.btn-auth:hover:not(:disabled) { background: var(--accent-soft); transform: translateY(-1px); box-shadow: var(--shadow-glow); }
.btn-auth:active:not(:disabled) { transform: translateY(0); }
.btn-auth:disabled { opacity: 0.5; }

.sending-spinner {
  width: 18px; height: 18px; border: 2px solid transparent;
  border-top-color: #0a0e14; border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.switch-link { text-align: center; margin-top: 22px; font-size: 14px; color: var(--text-secondary); }
.back-link { text-align: center; margin-top: 10px; font-size: 13px; }
</style>
