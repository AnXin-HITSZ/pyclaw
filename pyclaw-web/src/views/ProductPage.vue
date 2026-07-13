<template>
  <div class="product-page">
    <!-- Sticky Navbar -->
    <header class="product-navbar">
      <div class="navbar-inner">
        <router-link to="/" class="navbar-brand">PyClaw</router-link>
        <nav class="navbar-links">
          <router-link to="/">首页</router-link>
          <router-link to="/product" class="active">功能介绍</router-link>
          <router-link to="/login">登录</router-link>
          <router-link to="/register">注册</router-link>
        </nav>
      </div>
    </header>

    <!-- Hero Section -->
    <section class="product-hero">
      <div class="hero-text">
        <p class="hero-kicker">pyclaw workspace</p>
        <h1>把你的 Claw 部署到网页和飞书</h1>
        <p class="hero-desc">
          创建一个 Claw，绑定一个飞书群，再把前端、后端、运维、产品、算法等角色 Agent 放进同一个协作入口。
        </p>
        <div class="hero-actions">
          <router-link to="/login" class="btn-primary">开始使用</router-link>
          <a href="#features" class="btn-outline">了解更多</a>
        </div>
      </div>
      <div class="hero-preview" aria-hidden="true">
        <div class="preview-toolbar"><span></span><span></span><span></span></div>
        <div class="preview-body">
          <div class="preview-card"><small>Status</small><b>Running</b></div>
          <div class="preview-card"><small>Claws</small><b>3 active</b></div>
          <div class="preview-card"><small>Agents</small><b>5 online</b></div>
          <div class="preview-card"><small>Channel</small><b>Feishu</b></div>
        </div>
      </div>
    </section>

    <!-- Feature Grid -->
    <section id="features" class="product-features">
      <div class="feature-card">
        <span class="feature-icon">&#x1F9E9;</span>
        <h3>Claw Builder</h3>
        <p>创建和管理 Claw，绑定飞书群，配置多个角色 Agent，灵活定义每个角色的能力和权限。</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">&#x1F4AC;</span>
        <h3>飞书集成</h3>
        <p>将 Claw 部署到飞书群聊，通过 @提及 触发不同角色 Agent，支持群聊和私聊双模式。</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">&#x26A1;</span>
        <h3>Agent 运行</h3>
        <p>支持多种 LLM Provider，灵活配置工具策略（最小化 / 只读 / 消息 / 编码 / 完全），安全可控。</p>
      </div>
      <div class="feature-card">
        <span class="feature-icon">&#x1F6E0;</span>
        <h3>Provider 管理</h3>
        <p>统一的模型服务配置入口，支持 OpenAI 兼容接口和 DeepSeek，一键切换管理和启用状态。</p>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="product-cta">
      <h2>准备好开始了吗？</h2>
      <p>注册账号，创建你的第一个 Claw，体验多 Agent 协作的效率提升。</p>
      <router-link to="/register" class="btn-primary">立即注册</router-link>
    </section>

    <!-- Footer -->
    <footer class="product-footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <strong>PyClaw</strong>
          <small>Multi-agent workspace</small>
        </div>
        <div class="footer-links">
          <a href="#">关于</a>
          <a href="#">文档</a>
          <a href="https://github.com/anxin-hitsz" target="_blank" rel="noopener">GitHub</a>
        </div>
      </div>
      <p class="footer-copy">&copy; {{ year }} PyClaw &middot; Built by anxin-hitsz</p>
    </footer>
  </div>
</template>

<script setup>
const year = new Date().getFullYear()
</script>

<style scoped>
/* ---- Product Page — matching Bootstrap 5.3 Product example ---- */
.product-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  color: #212529;
  font-family: Inter, ui-sans-serif, system-ui, "Segoe UI", Arial, sans-serif;
}

/* ---- Navbar ---- */
.product-navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, .92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e9ecef;
}

.navbar-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: .85rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.navbar-brand {
  font-size: 1.25rem;
  font-weight: 800;
  color: #212529;
  text-decoration: none;
  letter-spacing: -.02em;
}

.navbar-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.navbar-links a {
  color: #495057;
  text-decoration: none;
  font-weight: 600;
  font-size: .95rem;
  padding: .35rem 0;
  border-bottom: 2px solid transparent;
  transition: color .15s, border-color .15s;
}

.navbar-links a:hover,
.navbar-links a.active {
  color: #212529;
  border-bottom-color: #212529;
}

/* ---- Hero ---- */
.product-hero {
  max-width: 1200px;
  margin: 3rem auto;
  padding: 0 1.5rem;
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 3rem;
}

.hero-text {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.hero-kicker {
  margin: 0;
  color: #6c757d;
  text-transform: uppercase;
  font-size: .78rem;
  font-weight: 800;
  letter-spacing: .08em;
}

.hero-text h1 {
  margin: 0;
  font-size: clamp(2.2rem, 4.5vw, 3.5rem);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -.02em;
}

.hero-desc {
  margin: 0;
  color: #6c757d;
  font-size: 1.15rem;
  line-height: 1.7;
  max-width: 520px;
}

.hero-actions {
  display: flex;
  gap: .75rem;
  margin-top: .5rem;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  padding: .7rem 1.6rem;
  font-size: .95rem;
  font-weight: 700;
  color: #fff;
  background: #212529;
  border: 2px solid #212529;
  border-radius: .375rem;
  text-decoration: none;
  transition: background-color .15s, border-color .15s;
}

.btn-primary:hover {
  background: #424649;
  border-color: #373b3e;
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  padding: .7rem 1.6rem;
  font-size: .95rem;
  font-weight: 700;
  color: #212529;
  background: transparent;
  border: 2px solid #6c757d;
  border-radius: .375rem;
  text-decoration: none;
  transition: background-color .15s, border-color .15s, color .15s;
}

.btn-outline:hover {
  background: #212529;
  border-color: #212529;
  color: #fff;
}

/* Hero Preview (device mockup) */
.hero-preview {
  background: #212529;
  border-radius: 1.5rem;
  padding: 1rem;
  box-shadow: 0 25px 60px rgba(0, 0, 0, .18);
  transform: rotate(1deg);
}

.preview-toolbar {
  display: flex;
  gap: 7px;
  padding: 0 .5rem .75rem;
  border-bottom: 1px solid rgba(255, 255, 255, .12);
}

.preview-toolbar span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .35);
}

.preview-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: .75rem;
  padding: 1rem .5rem .5rem;
}

.preview-card {
  background: #343a40;
  border-radius: .5rem;
  padding: 1rem;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: .35rem;
}

.preview-card small {
  font-size: .68rem;
  font-weight: 700;
  text-transform: uppercase;
  color: rgba(255, 255, 255, .55);
  letter-spacing: .05em;
}

.preview-card b {
  font-size: 1.15rem;
  font-weight: 700;
}

/* ---- Features ---- */
.product-features {
  max-width: 1200px;
  margin: 2rem auto 3rem;
  padding: 0 1.5rem;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
}

.feature-card {
  padding: 2rem;
  border: 1px solid #e9ecef;
  border-radius: .75rem;
  background: #fff;
  transition: box-shadow .2s, transform .2s;
}

.feature-card:hover {
  box-shadow: 0 12px 30px rgba(0, 0, 0, .08);
  transform: translateY(-2px);
}

.feature-icon {
  font-size: 2rem;
  display: block;
  margin-bottom: .75rem;
}

.feature-card h3 {
  margin: 0 0 .5rem;
  font-size: 1.2rem;
  font-weight: 700;
}

.feature-card p {
  margin: 0;
  color: #6c757d;
  line-height: 1.6;
  font-size: .95rem;
}

/* ---- CTA ---- */
.product-cta {
  max-width: 720px;
  margin: 1rem auto 3rem;
  padding: 2.5rem 1.5rem;
  text-align: center;
  background: #f8f9fa;
  border-radius: .75rem;
  border: 1px solid #e9ecef;
}

.product-cta h2 {
  margin: 0 0 .5rem;
  font-size: 1.75rem;
  font-weight: 800;
}

.product-cta p {
  margin: 0 0 1.5rem;
  color: #6c757d;
  font-size: 1.05rem;
}

.product-cta .btn-primary {
  font-size: 1rem;
  padding: .8rem 2rem;
}

/* ---- Footer ---- */
.product-footer {
  margin-top: auto;
  padding: 2rem 1.5rem 2.5rem;
  border-top: 1px solid #e9ecef;
  background: #f8f9fa;
}

.footer-inner {
  max-width: 1200px;
  margin: 0 auto 1.5rem;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.footer-brand {
  display: flex;
  flex-direction: column;
  gap: .25rem;
}

.footer-brand strong {
  font-size: 1.1rem;
}

.footer-brand small {
  color: #6c757d;
  font-size: .85rem;
}

.footer-links {
  display: flex;
  gap: 1.5rem;
}

.footer-links a {
  color: #6c757d;
  text-decoration: none;
  font-weight: 600;
  font-size: .9rem;
}

.footer-links a:hover {
  color: #212529;
  text-decoration: underline;
}

.footer-copy {
  max-width: 1200px;
  margin: 0 auto;
  text-align: center;
  color: #adb5bd;
  font-size: .82rem;
}

/* ---- Responsive ---- */
@media (max-width: 860px) {
  .product-hero {
    grid-template-columns: 1fr;
    gap: 2rem;
    margin: 2rem auto;
  }

  .hero-preview {
    transform: none;
  }

  .product-features {
    grid-template-columns: 1fr;
  }

  .navbar-links {
    gap: .75rem;
  }

  .navbar-links a {
    font-size: .85rem;
  }
}

@media (max-width: 540px) {
  .navbar-inner {
    flex-direction: column;
    align-items: flex-start;
    gap: .5rem;
  }

  .hero-actions {
    flex-direction: column;
  }

  .btn-primary,
  .btn-outline {
    text-align: center;
    justify-content: center;
  }

  .footer-inner {
    flex-direction: column;
    gap: 1rem;
  }
}
</style>
