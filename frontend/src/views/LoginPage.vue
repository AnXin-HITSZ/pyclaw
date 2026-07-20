<template>
  <div class="auth-page">
    <div class="aurora aurora-1"></div>
    <div class="aurora aurora-2"></div>
    <div class="auth-card">
      <div class="auth-brand">
        <span class="brand-mark">◢</span>
        <span class="brand-name">SaasClaw</span>
      </div>
      <h1>欢迎回来</h1>
      <p class="auth-sub">登录你的 Multi-Agent 工作区</p>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">用户名</label>
          <input id="username" v-model="username" type="text" required placeholder="请输入用户名" autocomplete="username" />
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <input id="password" v-model="password" type="password" required placeholder="请输入密码" autocomplete="current-password" />
        </div>
        <AppButton variant="primary" type="submit" class="auth-submit" :loading="submitting" loading-text="登录中...">
          登录
        </AppButton>
      </form>
      <p class="auth-switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../composables/useAuth.js";
import { useToast } from "../composables/useToast.js";
import AppButton from "../components/ui/AppButton.vue";

const router = useRouter();
const { login } = useAuth();
const { toast } = useToast();

const username = ref("");
const password = ref("");
const submitting = ref(false);

async function handleLogin() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    await login(username.value, password.value);
    router.push("/workspace");
  } catch (e) {
    toast.error(e.message || "登录失败");
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
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
.form-group { margin-bottom: 20px; }
label { display: block; margin-bottom: 6px; font-size: 13px; font-weight: 500; color: var(--text-secondary); }
input {
  width: 100%; padding: 10px 14px; background: var(--bg-deep); border: 1px solid var(--border);
  border-radius: var(--radius-sm); color: var(--text-primary); font-size: 14px;
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}
input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
.auth-submit { width: 100%; margin-top: 8px; padding: 12px; }
.auth-switch { margin-top: 22px; text-align: center; color: var(--text-muted); font-size: 13px; }
@media (prefers-reduced-motion: reduce) {
  .aurora-1, .aurora-2 { animation: none; }
  .auth-card { animation: none; }
}
</style>
