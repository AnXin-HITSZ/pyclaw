# pyclaw K3s + Helm 单机部署技术文档

本文档记录如何在一台阿里云 ECS 上使用 K3s 搭建单机 Kubernetes 环境，并使用 Helm 部署 `pyclaw-api`。

本文档面向当前项目状态：

- `pyclaw` 源码位于 ECS 的 `/opt/pyclaw`
- Docker 镜像 `pyclaw-api:dev` 已经可以在 ECS 上构建和运行
- `pyclaw-api` 已经通过 Docker 验证：
  - `GET /healthz`
  - `POST /v1/agent/run`
  - mock provider
  - 真实 OpenAI provider
  - transcript 持久化挂载目录
- 项目中已经存在 Helm Chart：
  - `/opt/pyclaw/helm/pyclaw`

## 1. 整体目标

当前目标不是直接做生产级高可用 Kubernetes，而是在单台 ECS 上完成从 Docker 到 Kubernetes 的过渡：

```text
ECS
  Docker
    pyclaw-api:dev 镜像
  K3s
    Deployment
    Service
    ConfigMap
    Secret
    PVC
    Helm Release
      pyclaw-api Pod
```

完成后，`pyclaw` 将不再通过 `docker run` 手动启动，而是由 Kubernetes 管理。

## 2. 为什么选择 K3s

K3s 是轻量级 Kubernetes 发行版。它仍然是 Kubernetes，但把很多安装和维护细节做了封装。

对当前阶段来说，K3s 的价值是：

1. 可以在单台 ECS 上运行。
2. 资源占用比完整 kubeadm 集群更低。
3. 默认自带 containerd、CoreDNS、Ingress Controller、local-path-provisioner 等基础组件。
4. 仍然可以学习标准 Kubernetes 对象：
   - Pod
   - Deployment
   - Service
   - ConfigMap
   - Secret
   - PVC
   - Ingress
   - Namespace
   - Helm

因此，K3s 适合作为 pyclaw 从 Docker 进入 Kubernetes 的第一步。

## 3. 部署前置条件

### 3.1 ECS 基础条件

建议 ECS 至少具备：

```text
CPU: 2 vCPU 或以上
Memory: 4 GiB 或以上更舒适
OS: Ubuntu 22.04 / Debian 系列
Disk: 40 GiB 或以上
```

如果只有 1 GiB 或 2 GiB 内存，也可以实验，但后续如果再安装 Rancher 会比较吃紧。

### 3.2 已完成内容

确认 Docker 可用：

```bash
sudo docker run hello-world
```

确认项目目录存在：

```bash
ls -la /opt/pyclaw
```

确认镜像存在：

```bash
sudo docker images | grep pyclaw-api
```

预期能看到：

```text
pyclaw-api   dev   ...
```

确认 Helm Chart 存在：

```bash
ls -la /opt/pyclaw/helm/pyclaw
```

预期能看到：

```text
Chart.yaml
values.yaml
templates/
```

## 4. 安装 K3s

### 4.1 官方安装方式

在 ECS 上执行：

```bash
curl -sfL https://get.k3s.io | sh -
```

该脚本会安装并启动 `k3s` systemd 服务。

### 4.2 国内网络备用安装方式

如果官方地址下载不稳定，可以使用 Rancher 国内镜像：

```bash
curl -sfL https://rancher-mirror.oss-cn-beijing.aliyuncs.com/k3s/k3s-install.sh | INSTALL_K3S_MIRROR=cn sh -
```

### 4.3 检查 K3s 服务

```bash
sudo systemctl status k3s --no-pager
```

如果服务正常，应看到：

```text
active (running)
```

### 4.4 检查节点

```bash
sudo k3s kubectl get nodes
```

预期类似：

```text
NAME                      STATUS   ROLES                  AGE   VERSION
iZwz9fiujj747aj9bulp8qZ   Ready    control-plane,master   1m    v1.xx.x+k3s...
```

`STATUS=Ready` 表示单机 K3s 集群已经可用。

### 4.5 检查系统 Pod

```bash
sudo k3s kubectl get pods -A
```

常见系统组件包括：

```text
kube-system   coredns-...
kube-system   local-path-provisioner-...
kube-system   metrics-server-...
kube-system   traefik-...
```

其中：

- `coredns` 负责集群 DNS。
- `local-path-provisioner` 负责单机本地 PVC。
- `metrics-server` 提供基础资源指标。
- `traefik` 是 K3s 默认安装的 Ingress Controller。

## 5. kubectl 使用方式

K3s 自带 kubectl，可以直接使用：

```bash
sudo k3s kubectl get nodes
```

为了少打几个字，也可以配置一个别名：

```bash
echo "alias kubectl='sudo k3s kubectl'" >> ~/.bashrc
source ~/.bashrc
```

之后可以使用：

```bash
kubectl get nodes
kubectl get pods -A
```

本文后续命令会优先写 `sudo k3s kubectl`，避免依赖别名。

## 6. 安装 Helm

Helm 是 Kubernetes 的应用打包和部署工具。当前项目已经准备好 Helm Chart，因此需要在 ECS 上安装 Helm。

### 6.1 官方安装方式

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

### 6.2 检查 Helm

```bash
helm version
```

预期能看到 Helm 版本信息。

### 6.3 如果 GitHub 下载不稳定

可以改用系统包或手动下载方式。Ubuntu 上可以先尝试：

```bash
sudo snap install helm --classic
```

如果服务器没有 snap，建议后续再根据实际网络情况选择 Helm 二进制包安装。

## 7. 让 Helm 连接 K3s

K3s 的 kubeconfig 默认在：

```text
/etc/rancher/k3s/k3s.yaml
```

由于该文件通常需要 root 权限，Helm 默认可能读不到。

推荐在当前 shell 中设置：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
```

如果当前用户不是 root，可能仍需使用 sudo：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm list -A
```

为了简单，本文部署命令优先使用：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm ...
```

## 8. K3s 使用本地 Docker 镜像的问题

这是最容易踩坑的地方。

你之前用 Docker 构建了：

```bash
sudo docker build -t pyclaw-api:dev .
```

但 K3s 默认使用的是 **containerd**，不是 Docker daemon。

这意味着：

```text
Docker 能看到 pyclaw-api:dev
不代表 K3s/containerd 能看到 pyclaw-api:dev
```

如果直接部署，Pod 可能出现：

```text
ImagePullBackOff
ErrImagePull
```

因为 K3s 会尝试拉取 `pyclaw-api:dev`，但本地 containerd 镜像仓库里没有它。

### 8.1 推荐方案：导入 Docker 镜像到 K3s containerd

在 `/opt/pyclaw` 下执行：

```bash
cd /opt/pyclaw
sudo docker save pyclaw-api:dev -o pyclaw-api-dev.tar
sudo k3s ctr images import pyclaw-api-dev.tar
```

检查 K3s containerd 是否能看到镜像：

```bash
sudo k3s ctr images list | grep pyclaw-api
```

如果能看到 `pyclaw-api:dev`，说明导入成功。

### 8.2 后续生产方案：推送到镜像仓库

学习阶段可以用 `docker save` + `k3s ctr images import`。

生产阶段更推荐：

1. 构建镜像。
2. 打 tag。
3. 推送到镜像仓库，例如阿里云 ACR。
4. Helm 中使用镜像仓库地址。

例如：

```yaml
image:
  repository: registry.cn-hangzhou.aliyuncs.com/your-namespace/pyclaw-api
  tag: 0.1.0
```

## 9. 创建 Namespace

建议单独创建 `pyclaw` 命名空间：

```bash
sudo k3s kubectl create namespace pyclaw
```

如果已经存在，会提示：

```text
Error from server (AlreadyExists)
```

这不是严重问题。

检查：

```bash
sudo k3s kubectl get ns
```

## 10. 创建 OpenAI Secret

不要把真实 API Key 写入 Git 或镜像。

在 K3s 中创建 Secret：

```bash
sudo k3s kubectl -n pyclaw create secret generic pyclaw-provider-secret \
  --from-literal=OPENAI_API_KEY='你的真实_api_key'
```

如果你使用 OpenAI 官方 Responses API，通常只需要：

```text
OPENAI_API_KEY
```

如果你使用 OpenAI-compatible 服务，还需要 `OPENAI_BASE_URL`：

```bash
sudo k3s kubectl -n pyclaw create secret generic pyclaw-provider-secret \
  --from-literal=OPENAI_API_KEY='你的真实_api_key' \
  --from-literal=OPENAI_BASE_URL='https://你的_base_url'
```

注意：如果 Secret 已存在，重新创建会失败。可以先删除再创建：

```bash
sudo k3s kubectl -n pyclaw delete secret pyclaw-provider-secret
```

然后重新执行 create。

检查 Secret 是否存在：

```bash
sudo k3s kubectl -n pyclaw get secret
```

不要直接打印 Secret 内容。

## 11. 准备 K3s 部署 values 文件

不要直接修改 `helm/pyclaw/values.yaml` 存放真实环境配置。

在 `/opt/pyclaw` 下创建一个本地 values 文件：

```bash
cd /opt/pyclaw
nano values-k3s.yaml
```

OpenAI 官方 Responses API 示例：

```yaml
image:
  repository: pyclaw-api
  tag: dev
  pullPolicy: IfNotPresent

secret:
  create: false
  existingSecret: pyclaw-provider-secret

env:
  OPENCLAW_CHATDATA_DIR: /app/chatdata
  OPENAI_MODEL: gpt-4.1-mini
  OPENAI_API_MODE: responses

ingress:
  enabled: false

persistence:
  enabled: true
  size: 5Gi
```

OpenAI-compatible 服务示例：

```yaml
image:
  repository: pyclaw-api
  tag: dev
  pullPolicy: IfNotPresent

secret:
  create: false
  existingSecret: pyclaw-provider-secret

env:
  OPENCLAW_CHATDATA_DIR: /app/chatdata
  OPENAI_MODEL: 你的模型名
  OPENAI_API_MODE: chat_completions

ingress:
  enabled: false

persistence:
  enabled: true
  size: 5Gi
```

建议把 `values-k3s.yaml` 加入 `.gitignore`，避免误提交真实环境配置。

## 12. 渲染 Helm Chart

部署前先只渲染，不实际创建资源：

```bash
cd /opt/pyclaw
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm template pyclaw ./helm/pyclaw -f values-k3s.yaml
```

这一步用于检查 Helm 模板语法。

如果输出一大段 Kubernetes YAML，说明 Helm 模板基本可用。

如果报错，需要根据报错定位到具体模板文件。

## 13. 使用 Helm 部署 pyclaw

执行：

```bash
cd /opt/pyclaw
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install pyclaw ./helm/pyclaw \
  -n pyclaw \
  --create-namespace \
  -f values-k3s.yaml
```

命令含义：

- `helm upgrade --install`：如果 release 不存在就安装，存在就升级。
- `pyclaw`：release 名称。
- `./helm/pyclaw`：Chart 路径。
- `-n pyclaw`：部署到 `pyclaw` namespace。
- `--create-namespace`：namespace 不存在时自动创建。
- `-f values-k3s.yaml`：使用当前环境的配置覆盖默认 values。

查看 release：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm list -n pyclaw
```

## 14. 查看 Kubernetes 资源

查看 Pod：

```bash
sudo k3s kubectl -n pyclaw get pods
```

查看 Deployment：

```bash
sudo k3s kubectl -n pyclaw get deploy
```

查看 Service：

```bash
sudo k3s kubectl -n pyclaw get svc
```

查看 PVC：

```bash
sudo k3s kubectl -n pyclaw get pvc
```

查看全部资源：

```bash
sudo k3s kubectl -n pyclaw get all
```

## 15. 验证服务

### 15.1 查看 Pod 是否 Running

```bash
sudo k3s kubectl -n pyclaw get pods
```

预期：

```text
NAME                          READY   STATUS    RESTARTS   AGE
pyclaw-xxxx                   1/1     Running   0          ...
```

### 15.2 查看日志

```bash
sudo k3s kubectl -n pyclaw logs deploy/pyclaw
```

预期看到 Uvicorn 启动日志：

```text
Uvicorn running on http://0.0.0.0:8000
```

### 15.3 端口转发

为了先验证服务，不急着配置 Ingress。

执行：

```bash
sudo k3s kubectl -n pyclaw port-forward svc/pyclaw 8000:8000
```

保持该命令运行。

另开一个 SSH 终端，执行：

```bash
curl http://localhost:8000/healthz
```

预期：

```json
{"status":"ok","service":"pyclaw-api"}
```

### 15.4 测试真实模型调用

```bash
curl -X POST http://localhost:8000/v1/agent/run \
  -H "Content-Type: application/json" \
  -d '{"prompt":"你好，请用一句话介绍你自己。","provider":"openai","session_id":"k3s-real-demo","tool_profile":"minimal"}'
```

预期返回 JSON，其中 `text` 字段包含模型回复。

## 16. 验证 transcript 持久化

查看 PVC：

```bash
sudo k3s kubectl -n pyclaw get pvc
```

进入 Pod 查看 `/app/chatdata`：

```bash
POD=$(sudo k3s kubectl -n pyclaw get pods -l app.kubernetes.io/name=pyclaw -o jsonpath='{.items[0].metadata.name}')
sudo k3s kubectl -n pyclaw exec -it "$POD" -- ls -la /app/chatdata
```

查看 transcript：

```bash
sudo k3s kubectl -n pyclaw exec -it "$POD" -- cat /app/chatdata/k3s-real-demo.jsonl
```

如果能看到 JSONL 内容，说明 session transcript 已经写入 PVC。

## 17. 常见问题排查

### 17.1 Pod ImagePullBackOff

查看 Pod：

```bash
sudo k3s kubectl -n pyclaw get pods
```

如果看到：

```text
ImagePullBackOff
ErrImagePull
```

通常是 K3s containerd 没有 `pyclaw-api:dev` 镜像。

解决：

```bash
cd /opt/pyclaw
sudo docker save pyclaw-api:dev -o pyclaw-api-dev.tar
sudo k3s ctr images import pyclaw-api-dev.tar
sudo k3s ctr images list | grep pyclaw-api
```

然后重启 Deployment：

```bash
sudo k3s kubectl -n pyclaw rollout restart deploy/pyclaw
```

### 17.2 Pod CrashLoopBackOff

查看日志：

```bash
sudo k3s kubectl -n pyclaw logs deploy/pyclaw
```

查看详细事件：

```bash
sudo k3s kubectl -n pyclaw describe pod <pod-name>
```

常见原因：

- 环境变量错误。
- Secret 不存在。
- PVC 挂载失败。
- 应用启动异常。

### 17.3 API 返回 500

查看日志：

```bash
sudo k3s kubectl -n pyclaw logs deploy/pyclaw --tail=200
```

可能原因：

- `OPENAI_API_KEY` 未注入。
- `OPENAI_MODEL` 不正确。
- `OPENAI_API_MODE` 与服务不匹配。
- OpenAI-compatible 服务需要 `OPENAI_BASE_URL`。
- transcript 目录权限异常。

### 17.4 Secret 未生效

检查 Secret：

```bash
sudo k3s kubectl -n pyclaw get secret pyclaw-provider-secret
```

检查 Deployment 是否引用了正确 Secret：

```bash
sudo k3s kubectl -n pyclaw describe deploy pyclaw
```

如果修改了 Secret，建议重启 Deployment：

```bash
sudo k3s kubectl -n pyclaw rollout restart deploy/pyclaw
```

### 17.5 PVC Pending

查看 PVC：

```bash
sudo k3s kubectl -n pyclaw get pvc
```

如果 PVC 一直是 `Pending`，查看 StorageClass：

```bash
sudo k3s kubectl get storageclass
```

K3s 默认通常有：

```text
local-path
```

如果没有，需要检查 `local-path-provisioner` 是否正常：

```bash
sudo k3s kubectl -n kube-system get pods | grep local-path
```

## 18. 升级 pyclaw

如果你修改了 pyclaw 源码，需要重新构建镜像：

```bash
cd /opt/pyclaw
sudo docker build -t pyclaw-api:dev .
```

重新导入到 K3s containerd：

```bash
sudo docker save pyclaw-api:dev -o pyclaw-api-dev.tar
sudo k3s ctr images import pyclaw-api-dev.tar
```

重启 Deployment：

```bash
sudo k3s kubectl -n pyclaw rollout restart deploy/pyclaw
```

查看 rollout 状态：

```bash
sudo k3s kubectl -n pyclaw rollout status deploy/pyclaw
```

## 19. 卸载 pyclaw

卸载 Helm release：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm uninstall pyclaw -n pyclaw
```

注意：默认情况下，PVC 可能不会随 Helm release 自动删除，避免误删数据。

查看 PVC：

```bash
sudo k3s kubectl -n pyclaw get pvc
```

如果确认不再需要数据，可以手动删除 PVC：

```bash
sudo k3s kubectl -n pyclaw delete pvc <pvc-name>
```

## 20. 卸载 K3s

如果只是卸载 pyclaw，不需要卸载 K3s。

如果确认要删除整个 K3s：

```bash
sudo /usr/local/bin/k3s-uninstall.sh
```

注意：这会删除 K3s 集群和相关数据。执行前确认不再需要其中的资源。

## 21. 与 Docker 单容器运行的区别

Docker 单容器模式：

```text
你手动 docker run
容器退出后需要自己处理
目录挂载由 -v 指定
环境变量由 --env-file 指定
端口由 -p 指定
```

K3s + Helm 模式：

```text
Deployment 负责维持 Pod 运行
Service 提供稳定访问入口
Secret 注入敏感配置
ConfigMap 注入普通配置
PVC 提供持久化存储
Helm 管理整套资源版本
```

这就是从“单容器运行”进入“云原生部署”的关键变化。

## 22. 后续演进建议

当前阶段建议先完成：

1. K3s 节点 Ready。
2. Helm Chart 能正常渲染。
3. pyclaw Pod Running。
4. `/healthz` 正常。
5. 真实模型调用正常。
6. transcript 能写入 PVC。

完成后再考虑：

1. 配置 Ingress，通过域名访问 pyclaw。
2. 安装 Rancher，用 UI 管理 K3s。
3. 使用阿里云 ACR 管理镜像。
4. 为 API 增加鉴权。
5. 引入异步任务队列。
6. 将 transcript / session 存储迁移到数据库或对象存储。

## 23. 最小执行清单

如果只看最短路径，可以按下面命令执行：

```bash
# 1. 安装 K3s
curl -sfL https://get.k3s.io | sh -

# 2. 检查节点
sudo k3s kubectl get nodes

# 3. 安装 Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
helm version

# 4. 导入 pyclaw 镜像到 K3s containerd
cd /opt/pyclaw
sudo docker save pyclaw-api:dev -o pyclaw-api-dev.tar
sudo k3s ctr images import pyclaw-api-dev.tar

# 5. 创建 namespace 和 secret
sudo k3s kubectl create namespace pyclaw
sudo k3s kubectl -n pyclaw create secret generic pyclaw-provider-secret \
  --from-literal=OPENAI_API_KEY='你的真实_api_key'

# 6. 创建 values-k3s.yaml
nano values-k3s.yaml

# 7. 渲染检查
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm template pyclaw ./helm/pyclaw -f values-k3s.yaml

# 8. 部署
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install pyclaw ./helm/pyclaw \
  -n pyclaw \
  --create-namespace \
  -f values-k3s.yaml

# 9. 查看 Pod
sudo k3s kubectl -n pyclaw get pods

# 10. 端口转发验证
sudo k3s kubectl -n pyclaw port-forward svc/pyclaw 8000:8000
```

另开终端测试：

```bash
curl http://localhost:8000/healthz

curl -X POST http://localhost:8000/v1/agent/run \
  -H "Content-Type: application/json" \
  -d '{"prompt":"你好，请用一句话介绍你自己。","provider":"openai","session_id":"k3s-real-demo","tool_profile":"minimal"}'
```

