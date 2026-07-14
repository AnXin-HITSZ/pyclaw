<template>
  <div class="page">
    <div class="page-header">
      <h1>Pod 状态</h1>
      <span class="subtitle">资源使用概览（数据为示例占位）</span>
    </div>

    <div class="stat-row">
      <div class="stat-card success"><div class="stat-value">3</div><div class="stat-label">Running Pods</div></div>
      <div class="stat-card"><div class="stat-value">0.45 / 2.0</div><div class="stat-label">CPU Cores</div></div>
      <div class="stat-card"><div class="stat-value">384 / 768</div><div class="stat-label">Memory MiB</div></div>
      <div class="stat-card accent"><div class="stat-value">2.4 / 5.0</div><div class="stat-label">Storage GiB</div></div>
    </div>

    <div class="pod-grid">
      <div class="card pod-card">
        <div class="pod-card-header">
          <h3>pyclaw-api</h3>
          <span class="pod-status running">Running</span>
        </div>
        <div class="metric-row">
          <div class="metric">
            <span class="metric-label">CPU</span>
            <div class="metric-bar"><div class="metric-fill" style="width:23%"></div></div>
            <span class="metric-value">0.23 / 1.0 核</span>
          </div>
          <div class="metric">
            <span class="metric-label">内存</span>
            <div class="metric-bar"><div class="metric-fill mem" style="width:45%"></div></div>
            <span class="metric-value">128 / 256 MiB</span>
          </div>
          <div class="metric">
            <span class="metric-label">存储</span>
            <div class="metric-bar"><div class="metric-fill disk" style="width:12%"></div></div>
            <span class="metric-value">1.2 / 10 GiB</span>
          </div>
        </div>
        <div class="pod-footer">
          <span>重启次数: 0</span>
          <span>运行时间: 3 天</span>
        </div>
      </div>

      <div class="pod-card">
        <div class="pod-card-header">
          <h3>spring-backend</h3>
          <span class="pod-status running">Running</span>
        </div>
        <div class="metric-row">
          <div class="metric">
            <span class="metric-label">CPU</span>
            <div class="metric-bar"><div class="metric-fill" style="width:35%"></div></div>
            <span class="metric-value">0.35 / 1.0 核</span>
          </div>
          <div class="metric">
            <span class="metric-label">内存</span>
            <div class="metric-bar"><div class="metric-fill mem" style="width:62%"></div></div>
            <span class="metric-value">320 / 512 MiB</span>
          </div>
          <div class="metric">
            <span class="metric-label">存储</span>
            <div class="metric-bar"><div class="metric-fill disk" style="width:8%"></div></div>
            <span class="metric-value">0.8 / 10 GiB</span>
          </div>
        </div>
        <div class="pod-footer">
          <span>重启次数: 0</span>
          <span>运行时间: 3 天</span>
        </div>
      </div>

      <div class="pod-card">
        <div class="pod-card-header">
          <h3>pyclaw-web</h3>
          <span class="pod-status running">Running</span>
        </div>
        <div class="metric-row">
          <div class="metric">
            <span class="metric-label">CPU</span>
            <div class="metric-bar"><div class="metric-fill" style="width:5%"></div></div>
            <span class="metric-value">0.05 / 0.3 核</span>
          </div>
          <div class="metric">
            <span class="metric-label">内存</span>
            <div class="metric-bar"><div class="metric-fill mem" style="width:28%"></div></div>
            <span class="metric-value">72 / 256 MiB</span>
          </div>
          <div class="metric">
            <span class="metric-label">存储</span>
            <div class="metric-bar"><div class="metric-fill disk" style="width:4%"></div></div>
            <span class="metric-value">0.4 / 10 GiB</span>
          </div>
        </div>
        <div class="pod-footer">
          <span>重启次数: 0</span>
          <span>运行时间: 3 天</span>
        </div>
      </div>
    </div>

    <p class="note">* 以上为静态占位数据，实际 Pod 资源监控接口尚未接入。</p>
  </div>
</template>

<script setup>
</script>

<style scoped>
.page { max-width: 1000px; }
.page-header { margin-bottom: 24px; }
.page-header h1 { font-size: 24px; }
.subtitle { color: var(--text-muted); font-size: 13px; margin-left: 12px; }
.pod-grid { display: grid; gap: 20px; }
.pod-card { cursor: default; }
.pod-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.pod-card-header h3 { font-size: 16px; font-family: monospace; }
.pod-status { font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.pod-status.running { background: rgba(63,185,80,0.15); color: var(--success); }
.metric-row { display: grid; gap: 16px; }
.metric { display: grid; grid-template-columns: 60px 1fr 120px; align-items: center; gap: 12px; font-size: 13px; }
.metric-label { color: var(--text-muted); }
.metric-bar { height: 8px; background: var(--bg-tertiary); border-radius: 4px; overflow: hidden; }
.metric-fill { height: 100%; background: var(--accent); border-radius: 4px; transition: width 0.5s; }
.metric-fill.mem { background: var(--warning); }
.metric-fill.disk { background: var(--success); }
.metric-value { color: var(--text-secondary); text-align: right; font-family: monospace; }
.pod-footer { display: flex; gap: 24px; margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--border-color); font-size: 12px; color: var(--text-muted); }
.note { margin-top: 24px; color: var(--text-muted); font-size: 12px; font-style: italic; }
</style>
