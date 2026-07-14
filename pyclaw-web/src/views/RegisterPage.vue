<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2>注册 PyClaw</h2>
      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <label for="username">用户名</label>
          <input id="username" v-model="username" type="text" required placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label for="displayName">显示名称（可选）</label>
          <input id="displayName" v-model="displayName" type="text" placeholder="如何称呼你？" />
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <input id="password" v-model="password" type="password" required placeholder="请设置密码" />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? "注册中..." : "注册" }}
        </button>
      </form>
      <p class="switch-link">
        已有账号？<router-link to="/login">立即登录</router-link>
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
const { register, loading } = useAuth();

const username = ref("");
const displayName = ref("");
const password = ref("");
const error = ref("");

async function handleRegister() {
  error.value = "";
  try {
    await register(username.value, password.value, displayName.value || undefined);
    router.push("/workspace");
  } catch (e) {
    error.value = e.message || "注册失败，请重试";
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

@keyframes modal-enter { from { opacity: 0; transform: scale(0.96) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }

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

.btn-primary {
  width: 100%; padding: 12px; font-size: 15px; font-weight: 700; color: #0a0e14;
  background: var(--accent); border: none; border-radius: var(--radius-sm);
  transition: all 0.2s var(--ease-out); min-height: 45px;
  display: flex; align-items: center; justify-content: center;
}
.btn-primary:hover:not(:disabled) { background: var(--accent-soft); transform: translateY(-1px); box-shadow: var(--shadow-glow); }
.btn-primary:active:not(:disabled) { transform: translateY(0); }
.btn-primary:disabled { opacity: 0.5; }

.switch-link { text-align: center; margin-top: 22px; font-size: 14px; color: var(--text-secondary); }
.back-link { text-align: center; margin-top: 10px; font-size: 13px; }
</style>
