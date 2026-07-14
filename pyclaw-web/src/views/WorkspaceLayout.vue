<template>
  <div class="workspace-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <router-link to="/workspace" class="logo-link">
          <svg width="30" height="30" viewBox="0 0 64 64" fill="none" class="logo-mark">
            <rect width="64" height="64" rx="16" fill="url(#claw-grad)" />
            <path d="M18 46V18l14 10-14 10z" fill="#0a0e14" />
            <path d="M32 38l14-10-14-10v20z" fill="#0a0e14" opacity="0.7" />
            <defs>
              <linearGradient id="claw-grad" x1="0" y1="0" x2="64" y2="64">
                <stop offset="0%" stop-color="#f0a33a" />
                <stop offset="100%" stop-color="#c78a2e" />
              </linearGradient>
            </defs>
          </svg>
          <span class="logo-text">PyClaw</span>
        </router-link>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-section">
          <span class="nav-section-title">工作台</span>
          <router-link to="/workspace/claws" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F980;</span> Claw 管理
          </router-link>
          <router-link to="/workspace/playground" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F4AC;</span> Agent 对话
          </router-link>
          <router-link to="/workspace/tools" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F527;</span> 工具目录
          </router-link>
          <router-link to="/workspace/pods" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F4E6;</span> Pod 状态
          </router-link>
        </div>

        <div class="nav-section">
          <span class="nav-section-title">配置</span>
          <router-link to="/workspace/agents" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F916;</span> Agent 配置
          </router-link>
          <router-link to="/workspace/providers" class="nav-item" active-class="active">
            <span class="nav-icon">&#x26A1;</span> Provider 管理
          </router-link>
          <router-link to="/workspace/secrets" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F512;</span> Secret 管理
          </router-link>
          <router-link to="/workspace/tokens" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F511;</span> API Token
          </router-link>
        </div>

        <div v-if="isAdmin" class="nav-section">
          <span class="nav-section-title">管理后台</span>
          <router-link to="/workspace/admin/users" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F465;</span> 用户管理
          </router-link>
          <router-link to="/workspace/admin/channels" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F4E1;</span> 渠道管理
          </router-link>
          <router-link to="/workspace/admin/audit" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F4CB;</span> 审计日志
          </router-link>
          <router-link to="/workspace/admin/usage" class="nav-item" active-class="active">
            <span class="nav-icon">&#x1F4CA;</span> 用量统计
          </router-link>
        </div>
      </nav>

      <div class="sidebar-footer">
        <span class="user-info">{{ user?.username }}</span>
        <button class="btn-logout" @click="handleLogout">退出</button>
      </div>
    </aside>

    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";
import { useAuth } from "../composables/useAuth.js";

const router = useRouter();
const { user, isAdmin, logout } = useAuth();

function handleLogout() {
  logout();
  router.push("/");
}
</script>

<style scoped>
.workspace-layout { display: flex; min-height: 100vh; }

.sidebar {
  width: var(--sidebar-width);
  background: var(--bg-surface);
  border-right: 1px solid var(--border);
  display: flex; flex-direction: column;
  position: fixed; top: 0; left: 0; bottom: 0; z-index: 10;
}

.sidebar-header { padding: 20px; border-bottom: 1px solid var(--border); }

.logo-link {
  display: flex; align-items: center; gap: 10px;
  color: var(--text-primary); text-decoration: none;
}
.logo-mark { transition: transform 0.3s var(--ease-spring); }
.logo-link:hover .logo-mark { transform: rotate(-8deg) scale(1.05); }

.logo-text {
  font-size: 18px; font-weight: 800;
  background: linear-gradient(135deg, var(--accent), var(--accent-soft));
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.5px;
}

.sidebar-nav { flex: 1; overflow-y: auto; padding: 12px 0; }

.nav-section { margin-bottom: 8px; }

.nav-section-title {
  display: block; padding: 8px 20px 4px;
  font-size: 10px; font-weight: 700; text-transform: uppercase;
  letter-spacing: 0.8px; color: var(--text-muted);
}

.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 16px; margin: 0 8px; border-radius: var(--radius-sm);
  font-size: 13px; font-weight: 500; color: var(--text-secondary);
  text-decoration: none;
  transition: all 0.2s var(--ease-out);
  position: relative;
}

.nav-item::before {
  content: ""; position: absolute; left: 0; top: 50%; transform: translateY(-50%);
  width: 2px; height: 0; background: var(--accent); border-radius: 1px;
  transition: height 0.25s var(--ease-spring);
}

.nav-item:hover { background: var(--bg-raised); color: var(--text-primary); }
.nav-item:hover::before { height: 16px; }

.nav-item.active {
  background: var(--accent-glow); color: var(--accent);
}
.nav-item.active::before { height: 20px; }

.nav-icon { width: 20px; text-align: center; font-size: 15px; transition: transform 0.2s var(--ease-spring); }
.nav-item:hover .nav-icon { transform: scale(1.15); }

.sidebar-footer {
  padding: 16px 20px; border-top: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between;
}

.user-info { font-size: 13px; color: var(--text-secondary); font-weight: 500; }

.btn-logout {
  padding: 5px 14px; font-size: 12px; font-weight: 500;
  color: var(--text-muted); background: transparent;
  border: 1px solid var(--border); border-radius: var(--radius-sm);
  transition: all 0.2s var(--ease-out);
}
.btn-logout:hover { color: var(--danger); border-color: var(--danger); background: rgba(248,81,73,0.08); }

.main-content { flex: 1; margin-left: var(--sidebar-width); padding: 32px; min-height: 100vh; }

/* Page transition */
.page-enter-active { transition: opacity 0.2s var(--ease-out), transform 0.2s var(--ease-out); }
.page-leave-active { transition: opacity 0.1s var(--ease-out); }
.page-enter-from { opacity: 0; transform: translateY(6px); }
.page-leave-to { opacity: 0; }
</style>
