<template>
  <div class="auth-shell">
    <header class="auth-header">
      <router-link to="/" class="auth-brand"><b>P</b><span>PyClaw</span></router-link>
      <nav class="auth-nav">
        <button :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
      </nav>
    </header>

    <section class="auth-hero">
      <div class="auth-copy">
        <p class="auth-kicker">Multi-agent workspace</p>
        <h1>开启你的 Claw 工作空间</h1>
        <p class="auth-lead">创建 Claw，绑定飞书群，把前端、后端、运维、产品、算法等角色 Agent 放进同一个协作入口。</p>
      </div>

      <form v-if="mode === 'login'" class="auth-card" @submit.prevent="login">
        <h2>登录</h2>
        <p>进入控制台，管理你的 Claw、Agent 与飞书绑定。</p>
        <label>后端地址<input v-model="apiBase" placeholder="留空使用同域" /></label>
        <label>用户名<input v-model="loginForm.username" autocomplete="username" /></label>
        <label>密码<input v-model="loginForm.password" type="password" autocomplete="current-password" /></label>
        <button class="btn btn-primary">登录</button>
        <p class="auth-switch">还没有账号？<button type="button" @click="switchMode('register')">立即注册</button></p>
        <p v-if="error" class="form-error">{{ error }}</p>
      </form>

      <form v-else class="auth-card" @submit.prevent="register">
        <h2>创建账号</h2>
        <p>注册后会获得创建 Claw、运行 Agent 和管理个人 Token 的基础权限。</p>
        <label>后端地址<input v-model="apiBase" placeholder="留空使用同域" /></label>
        <label>用户名<input v-model="registerForm.username" autocomplete="username" minlength="3" maxlength="64" /></label>
        <label>显示名称<input v-model="registerForm.displayName" autocomplete="name" /></label>
        <label>密码<input v-model="registerForm.password" type="password" autocomplete="new-password" minlength="8" /></label>
        <button class="btn btn-primary">创建账号</button>
        <p class="auth-switch">已有账号？<button type="button" @click="switchMode('login')">返回登录</button></p>
        <p v-if="error" class="form-error">{{ error }}</p>
      </form>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const props = defineProps({ mode: { type: String, default: 'login' } })

const TOKEN_KEY = 'pyclaw.console.token'
const BASE_KEY = 'pyclaw.console.baseUrl'

const router = useRouter()
const route = useRoute()

const mode = ref(props.mode)
const apiBase = ref(localStorage.getItem(BASE_KEY) || '')
const error = ref('')
const loading = ref(false)

const loginForm = reactive({ username: 'admin', password: '' })
const registerForm = reactive({ username: '', password: '', displayName: '' })

onMounted(() => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) checkAndRedirect()
})

function switchMode(m) {
  mode.value = m
  error.value = ''
  router.replace({ name: m === 'login' ? 'login' : 'register' })
}

function ep(p) {
  return `${apiBase.value.trim().replace(/\/$/, '')}${p}`
}

async function api(path, opt = {}) {
  const headers = { ...(opt.headers || {}) }
  if (!(opt.body instanceof FormData)) headers['Content-Type'] = headers['Content-Type'] || 'application/json'
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) headers.Authorization = `Bearer ${token}`
  const r = await fetch(ep(path), { ...opt, headers })
  if (!r.ok) throw new Error(await err(r))
  if (r.status === 204) return null
  const t = await r.text()
  return t ? JSON.parse(t) : null
}

async function err(r) {
  const t = await r.text()
  try { const d = JSON.parse(t); return d.message || d.detail || t } catch { return t || `${r.status} ${r.statusText}` }
}

async function login() {
  await load(async () => {
    localStorage.setItem(BASE_KEY, apiBase.value.trim())
    const d = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: loginForm.username, password: loginForm.password })
    })
    localStorage.setItem(TOKEN_KEY, d.accessToken)
    const redirect = route.query.redirect || '/console'
    router.replace(redirect)
  })
}

async function register() {
  await load(async () => {
    localStorage.setItem(BASE_KEY, apiBase.value.trim())
    const d = await api('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: registerForm.username,
        password: registerForm.password,
        displayName: registerForm.displayName || null
      })
    })
    localStorage.setItem(TOKEN_KEY, d.accessToken)
    Object.assign(registerForm, { username: '', password: '', displayName: '' })
    router.replace('/console')
  })
}

async function checkAndRedirect() {
  try {
    await api('/api/auth/me')
    router.replace('/console')
  } catch {
    localStorage.removeItem(TOKEN_KEY)
  }
}

async function load(fn) {
  loading.value = true
  error.value = ''
  try { await fn() } catch (e) { error.value = e.message || String(e) }
  finally { loading.value = false }
}
</script>

<style scoped>
/* ---- Auth page — dark cover with login/register card ---- */
.auth-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #212529;
  color: #fff;
  box-shadow: inset 0 0 0 100vmax rgba(0, 0, 0, .18);
  overflow: hidden;
}

.auth-header {
  width: min(1120px, calc(100% - 40px));
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 0;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;
  font-size: 1.12rem;
  letter-spacing: .2px;
  color: #fff;
  text-decoration: none;
}

.auth-brand b {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #0d6efd, #0dcaf0);
  color: #fff;
  box-shadow: 0 8px 20px rgba(13, 110, 253, .28);
}

.auth-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auth-nav button {
  border: 0;
  border-bottom: 3px solid transparent;
  background: transparent;
  color: rgba(255, 255, 255, .72);
  padding: 8px 4px;
  font-weight: 800;
  cursor: pointer;
  font-size: .95rem;
}

.auth-nav button:hover,
.auth-nav button.active {
  color: #fff;
  border-bottom-color: #fff;
}

.auth-hero {
  width: min(1120px, calc(100% - 40px));
  margin: auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 420px);
  align-items: center;
  gap: 48px;
  padding: 32px 0 72px;
}

.auth-copy {
  display: grid;
  gap: 22px;
  max-width: 680px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, .45);
}

.auth-kicker {
  margin: 0;
  color: #9ec5fe;
  text-transform: uppercase;
  font-size: .82rem;
  font-weight: 900;
  letter-spacing: .08em;
}

.auth-copy h1 {
  margin: 0;
  font-size: 3.2rem;
  line-height: 1.05;
  letter-spacing: 0;
}

.auth-lead {
  margin: 0;
  color: rgba(255, 255, 255, .78);
  font-size: 1.18rem;
  line-height: 1.72;
  max-width: 620px;
}

.auth-card {
  width: 100%;
  display: grid;
  gap: 15px;
  background: rgba(255, 255, 255, .96);
  border: 1px solid rgba(255, 255, 255, .2);
  border-radius: 8px;
  padding: 28px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, .32);
  color: #212529;
}

.auth-card h2 {
  margin: 0;
  font-size: 1.55rem;
}

.auth-card p {
  margin: 0;
  color: #6c757d;
  line-height: 1.5;
  font-size: .92rem;
}

.auth-card label {
  display: grid;
  gap: 7px;
  color: #495057;
  font-size: .86rem;
  font-weight: 750;
}

.auth-card input {
  width: 100%;
  min-width: 0;
  border: 1px solid #ced4da;
  border-radius: 6px;
  background: #fff;
  color: #212529;
  padding: 10px 12px;
  outline: none;
  transition: border-color .15s, box-shadow .15s;
  font: inherit;
  box-sizing: border-box;
}

.auth-card input:focus {
  border-color: #86b7fe;
  box-shadow: 0 0 0 .22rem rgba(13, 110, 253, .15);
}

.auth-switch {
  font-size: .9rem;
  text-align: center;
  color: #6c757d;
}

.auth-switch button {
  border: 0;
  background: transparent;
  color: #0d6efd;
  font-weight: 850;
  padding: 0;
  cursor: pointer;
}

.form-error {
  margin: 0;
  color: #b02a37;
  font-weight: 700;
  font-size: .9rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  background: #fff;
  color: #212529;
  padding: 9px 13px;
  font-weight: 750;
  cursor: pointer;
  font: inherit;
  min-height: 38px;
}

.btn-primary {
  background: #0d6efd;
  border-color: #0d6efd;
  color: #fff;
}

.btn-primary:hover {
  filter: brightness(.95);
}

/* ---- Responsive ---- */
@media (max-width: 900px) {
  .auth-hero {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .auth-copy {
    max-width: none;
  }

  .auth-card {
    max-width: 520px;
  }

  .auth-copy h1 {
    font-size: 2.25rem;
  }

  .auth-lead {
    font-size: 1rem;
  }
}

@media (max-width: 540px) {
  .auth-header {
    width: calc(100% - 28px);
    padding: 18px 0;
    align-items: flex-start;
    flex-direction: column;
  }

  .auth-hero {
    width: calc(100% - 28px);
    padding: 18px 0 36px;
  }

  .auth-card {
    padding: 20px;
  }
}
</style>
