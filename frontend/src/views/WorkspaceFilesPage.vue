<template>
  <div class="files-page">
    <PageHeader :title="`工作区文件`" :subtitle="currentPath">
      <template #actions>
        <AppButton variant="ghost" @click="$router.push(`/workspace/claws/${clawId}`)">← Claw 详情</AppButton>
      </template>
    </PageHeader>

    <div v-if="loading" class="files-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>大小</th>
            <th>修改时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in 5" :key="i">
            <td><AppSkeleton variant="text" :width="`${60 + i * 6}%`" :height="14" /></td>
            <td><AppSkeleton variant="text" width="48px" :height="14" /></td>
            <td><AppSkeleton variant="text" width="96px" :height="14" /></td>
            <td><AppSkeleton variant="text" width="56px" :height="14" /></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else-if="error" class="error-panel">
      <p class="error-msg">{{ error }}</p>
      <AppButton variant="ghost" @click="loadDir(currentPath)">重试</AppButton>
    </div>

    <div v-else class="file-manager">
      <!-- File List -->
      <div class="card files-card">
        <h3>文件列表</h3>
        <AppEmpty v-if="entries.length === 0" icon="📁" title="目录为空" description="当前路径下没有可显示的文件或子目录。" />
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>大小</th>
              <th>修改时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="entry in entries"
              :key="entry.name"
              class="file-row"
              @click="entry.isDir ? navigateTo(entry) : openFile(entry)"
            >
              <td>
                <span class="file-icon">{{ fileIcon(entry) }}</span>
                <span class="file-name">{{ entry.name }}</span>
              </td>
              <td class="file-size">{{ entry.isDir ? '—' : formatSize(entry.size) }}</td>
              <td class="file-mtime">{{ formatMtime(entry.mtime) }}</td>
              <td class="file-action">
                <button
                  v-if="!entry.isDir"
                  type="button"
                  class="link-btn"
                  @click.stop="openFile(entry)"
                >打开</button>
                <button
                  v-else
                  type="button"
                  class="link-btn"
                  @click.stop="navigateTo(entry)"
                >进入</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- File Editor -->
      <div v-if="selectedFile" class="card editor-card">
        <h3>{{ selectedFile }}</h3>
        <textarea v-model="fileContent" rows="15" class="file-editor" />
        <div class="editor-actions">
          <AppButton variant="ghost" @click="closeFile">取消</AppButton>
          <AppButton variant="primary" :loading="saving" loading-text="保存中..." @click="saveFile">保存</AppButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api/client.js";
import { useToast } from "../composables/useToast.js";
import AppButton from "../components/ui/AppButton.vue";
import AppSkeleton from "../components/ui/AppSkeleton.vue";
import AppEmpty from "../components/ui/AppEmpty.vue";
import PageHeader from "../components/ui/PageHeader.vue";

const { toast } = useToast();
const route = useRoute();
const clawId = ref(route.params.id);
const currentPath = ref(".");
const entries = ref([]);
const loading = ref(true);
const error = ref("");
const selectedFile = ref(null);
const fileContent = ref("");
const saving = ref(false);

const IMAGE_EXT = ["png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico"];

async function loadDir(path) {
  loading.value = true;
  error.value = "";
  try {
    const data = await api.get(`/api/claws/${clawId.value}/sandbox/files?path=${encodeURIComponent(path)}`);
    // Runner returns {path, items} — extract items array
    const rawItems = Array.isArray(data) ? data : (Array.isArray(data?.items) ? data.items : []);
    if (data?.path) currentPath.value = data.path;
    entries.value = rawItems.map(e => ({
      name: typeof e === "string" ? e : (e.name || e.path || ""),
      path: typeof e === "object" ? (e.path || e.name || "") : (typeof e === "string" ? e : ""),
      isDir: typeof e === "object" ? (e.type === "directory" || e.is_dir === true || e.isDir === true) : false,
      size: typeof e === "object" ? e.size : null,
      mtime: typeof e === "object" ? (e.mtime || e.modifiedAt || e.updatedAt || e.lastModified) : null,
    })).filter(e => e.name);
  } catch (e) {
    error.value = "获取文件列表失败: " + e.message;
  } finally {
    loading.value = false;
  }
}

function navigateTo(entry) {
  const targetPath = entry.path || entry.name;
  const newPath = currentPath.value === "." ? targetPath : currentPath.value + "/" + targetPath;
  currentPath.value = newPath;
  loadDir(newPath);
}

async function openFile(entry) {
  const targetPath = entry.path || entry.name;
  const filePath = currentPath.value === "." ? targetPath : currentPath.value + "/" + targetPath;
  try {
    const data = await api.get(`/api/claws/${clawId.value}/sandbox/files/${encodeURIComponent(filePath)}`);
    selectedFile.value = targetPath;
    // Runner returns {path, content} — extract content field
    fileContent.value = data && typeof data === "object" && "content" in data
      ? data.content
      : (typeof data === "string" ? data : JSON.stringify(data, null, 2));
  } catch (e) {
    toast.error("读取文件失败: " + e.message);
  }
}

async function saveFile() {
  if (saving.value) return;
  const filePath = currentPath.value === "." ? selectedFile.value : currentPath.value + "/" + selectedFile.value;
  saving.value = true;
  try {
    // Send {content: "..."} as runner expects JSON body
    await api.put(`/api/claws/${clawId.value}/sandbox/files/${encodeURIComponent(filePath)}`, {
      content: fileContent.value,
    });
    toast.success("保存成功");
  } catch (e) {
    toast.error("保存失败: " + e.message);
  } finally {
    saving.value = false;
  }
}

function closeFile() {
  selectedFile.value = null;
  fileContent.value = "";
}

function fileIcon(entry) {
  if (entry.isDir) return "📁";
  const ext = (entry.name.split(".").pop() || "").toLowerCase();
  if (IMAGE_EXT.includes(ext)) return "🖼";
  return "📄";
}

function formatSize(bytes) {
  if (!bytes) return "—";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / 1048576).toFixed(1) + " MB";
}

function formatMtime(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";
  const pad = n => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

onMounted(() => loadDir("."));
</script>

<style scoped>
.files-page { max-width: 900px; }
.file-manager { display: grid; grid-template-columns: 1fr; gap: 20px; }
.files-card { overflow: hidden; }
.files-card h3 { font-size: 15px; margin-bottom: 12px; }
.file-row { cursor: pointer; }
.file-row .file-icon { margin-right: 8px; font-size: 15px; }
.file-row .file-name { font-family: var(--font-mono); }
.file-size, .file-mtime { color: var(--text-muted); font-size: 12px; }
.file-action { text-align: right; }
.link-btn {
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s var(--ease-out), border-color 0.15s var(--ease-out);
}
.link-btn:hover { background: var(--accent-glow); border-color: var(--border-light); }
.file-editor {
  width: 100%;
  padding: 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 13px;
  resize: vertical;
}
.editor-actions { display: flex; gap: 8px; margin-top: 12px; }
.error-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px 24px;
}
.error-panel .error-msg { margin: 0; color: var(--danger); }
</style>
