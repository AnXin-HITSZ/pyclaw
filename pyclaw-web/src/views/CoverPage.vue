<template>
  <div class="cover-page">
    <div class="cover-container">
      <header class="cover-header">
        <h3 class="cover-brand">PyClaw</h3>
        <nav class="cover-nav">
          <router-link to="/" class="nav-link active">首页</router-link>
          <router-link to="/product" class="nav-link">功能介绍</router-link>
          <router-link to="/login" class="nav-link">登录</router-link>
        </nav>
      </header>

      <main class="cover-main">
        <h1>开启你的<br />PyClaw 工作空间</h1>
        <p class="cover-lead">
          为团队创建 AI Agent 协作入口，将前端、后端、运维、产品、算法等角色 Agent 放进同一个工作空间，支持网页和飞书双通道交互。
        </p>
        <p class="cover-cta">
          <router-link to="/product" class="btn-cover">
            开启我的 PyClaw 之旅
          </router-link>
        </p>
      </main>

      <footer class="cover-footer">
        <p>
          &copy; {{ year }} PyClaw &middot;
          Built by <a href="https://github.com/anxin-hitsz" target="_blank" rel="noopener">anxin-hitsz</a>
        </p>
      </footer>
    </div>
  </div>
</template>

<script setup>
const year = new Date().getFullYear()
</script>

<style scoped>
/* ---- Cover page — matching Bootstrap 5.3 Cover example ---- */
.cover-page {
  min-height: 100vh;
  display: flex;
  background-color: #212529;
  color: #fff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, .5);
  box-shadow: inset 0 0 5rem rgba(0, 0, 0, .5);
}

.cover-container {
  width: 100%;
  max-width: 42em;
  margin: 0 auto;
  padding: 1.5rem 1rem;
  display: flex;
  flex-direction: column;
}

/* ---- Header ---- */
.cover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: auto;
  padding-bottom: 1rem;
}

.cover-brand {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: #fff;
  letter-spacing: -.02em;
}

.cover-nav {
  display: flex;
  gap: 0;
}

.cover-nav .nav-link {
  padding: .5rem .25rem;
  font-weight: 700;
  color: rgba(255, 255, 255, .55);
  text-decoration: none;
  border-bottom: .15rem solid transparent;
  transition: color .15s ease-in-out, border-color .15s ease-in-out;
}

.cover-nav .nav-link + .nav-link {
  margin-left: 1rem;
}

.cover-nav .nav-link:hover,
.cover-nav .nav-link:focus {
  color: rgba(255, 255, 255, .85);
  border-bottom-color: rgba(255, 255, 255, .35);
}

.cover-nav .nav-link.active {
  color: #fff;
  border-bottom-color: #fff;
}

/* ---- Main ---- */
.cover-main {
  text-align: center;
  padding: 0 1rem;
}

.cover-main h1 {
  margin: 0 0 .5rem;
  font-size: clamp(2.25rem, 5vw, 3.5rem);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -.02em;
}

.cover-lead {
  margin: 1.25rem auto 1.75rem;
  max-width: 34rem;
  font-size: 1.2rem;
  font-weight: 300;
  line-height: 1.7;
  color: rgba(255, 255, 255, .78);
}

.cover-cta {
  margin: 0;
}

.btn-cover {
  display: inline-block;
  padding: .75rem 1.75rem;
  font-size: 1.05rem;
  font-weight: 700;
  color: #212529;
  text-decoration: none;
  background-color: #fff;
  border: 2px solid #fff;
  border-radius: .375rem;
  transition: color .15s ease-in-out, background-color .15s ease-in-out, border-color .15s ease-in-out;
  text-shadow: none;
}

.btn-cover:hover,
.btn-cover:focus {
  background-color: #e2e6ea;
  border-color: #dae0e5;
}

/* ---- Footer ---- */
.cover-footer {
  margin-top: auto;
  text-align: center;
  color: rgba(255, 255, 255, .55);
  font-size: .875rem;
}

.cover-footer p {
  margin: 0;
}

.cover-footer a {
  color: #fff;
  text-decoration: none;
  font-weight: 600;
}

.cover-footer a:hover {
  text-decoration: underline;
}

/* ---- Responsive ---- */
@media (max-width: 640px) {
  .cover-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .cover-nav {
    width: 100%;
    justify-content: flex-start;
  }

  .cover-main h1 {
    font-size: 2rem;
  }

  .cover-lead {
    font-size: 1.05rem;
  }
}
</style>
