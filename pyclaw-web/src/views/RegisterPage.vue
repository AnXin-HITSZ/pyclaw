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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 40px;
  width: 400px;
  max-width: 90vw;
}

h2 {
  text-align: center;
  margin-bottom: 32px;
  font-size: 24px;
}

.form-group {
  margin-bottom: 20px;
}

label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: var(--text-secondary);
}

input {
  width: 100%;
  padding: 10px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 14px;
}

input:focus {
  outline: none;
  border-color: var(--accent);
}

.error {
  color: var(--danger);
  font-size: 13px;
  margin-bottom: 16px;
}

.btn-primary {
  width: 100%;
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
  border: none;
  border-radius: 6px;
  transition: background 0.2s;
}

.btn-primary:hover:not(:disabled) {
  background: var(--accent-hover);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.switch-link {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--text-secondary);
}

.back-link {
  text-align: center;
  margin-top: 12px;
  font-size: 13px;
}
</style>
