# PyClaw 每用户 Namespace 沙箱实现记录

> 日期：2026-07-14
> 目标：按 SaaS 租户隔离模型实现“每个用户一个 Kubernetes namespace，每个 Claw 一个 Deployment + PVC + Service”。

## 设计选择

本次实现采用：

```text
User = Tenant
每个 User 一个 namespace
每个 Claw 在所属 User namespace 内创建独立 sandbox runner
```

资源命名规则：

```text
namespace: pyclaw-user-{userId}
PVC: workspace-{clawId}
Deployment: sandbox-runner-{clawId}
Service: sandbox-runner-{clawId}
```

所有资源都会带上标签：

```text
pyclaw.io/owner-user-id={userId}
pyclaw.io/claw-id={clawId}
app.kubernetes.io/part-of=pyclaw
app.kubernetes.io/component=sandbox-runner
```

## 后端实现

新增 `SandboxOrchestratorService`：

- 用户注册时创建用户 namespace
- 管理员创建用户时创建用户 namespace
- Claw 创建时创建/更新该 Claw 的 PVC、Deployment、Service
- Claw 更新时幂等同步 runner 资源
- Claw 删除时删除该 Claw 的 Service、Deployment、PVC

新增配置类：

```text
PyclawSandboxProperties
```

默认关闭：

```yaml
pyclaw.sandbox.enabled=false
```

开启后必须配置 runner 镜像：

```text
PYCLAW_SANDBOX_ENABLED=true
PYCLAW_SANDBOX_RUNNER_IMAGE=<sandbox-runner-image>
```

## Helm 实现

`spring-backend/helm` 新增：

- ServiceAccount
- ClusterRole
- ClusterRoleBinding

权限范围：

```text
namespaces: get/list/create/patch/update
persistentvolumeclaims/services: get/list/create/patch/update/delete
deployments.apps: get/list/create/patch/update/delete
```

Spring 后端 Deployment 使用该 ServiceAccount 调用 Kubernetes API。

## 生产启用示例

在 ECS 私有 `spring-values-k3s.yaml` 中配置：

```yaml
env:
  PYCLAW_SANDBOX_ENABLED: "true"
  PYCLAW_SANDBOX_NAMESPACE_PREFIX: pyclaw-user
  PYCLAW_SANDBOX_RUNNER_IMAGE: "crpi-li78f6lp5zheaj11.cn-shenzhen.personal.cr.aliyuncs.com/pyclaw/sandbox-runner:<tag>"
  PYCLAW_SANDBOX_RUNNER_IMAGE_PULL_POLICY: IfNotPresent
  PYCLAW_SANDBOX_RUNNER_PORT: "8000"
  PYCLAW_SANDBOX_WORKSPACE_MOUNT_PATH: /workspace
  PYCLAW_SANDBOX_PVC_STORAGE_SIZE: 1Gi
  PYCLAW_SANDBOX_CPU_REQUEST: 100m
  PYCLAW_SANDBOX_MEMORY_REQUEST: 256Mi
  PYCLAW_SANDBOX_CPU_LIMIT: 500m
  PYCLAW_SANDBOX_MEMORY_LIMIT: 768Mi
```

## 验证命令

创建用户后：

```bash
kubectl get ns -l app.kubernetes.io/part-of=pyclaw
```

创建 Claw 后：

```bash
kubectl -n pyclaw-user-<userId> get deploy,svc,pvc
kubectl -n pyclaw-user-<userId> get deploy,svc,pvc -l pyclaw.io/claw-id=<clawId>
```

删除 Claw 后：

```bash
kubectl -n pyclaw-user-<userId> get deploy,svc,pvc -l pyclaw.io/claw-id=<clawId>
```

应为空。

## 当前边界

- 用户 namespace 删除尚未自动执行；禁用用户不会删除 namespace。
- 已存在用户不会自动补 namespace，需在后续登录/管理动作或运维脚本中补齐。
- 已存在 Claw 不会自动补 runner，更新该 Claw 或后续增加批量 sync API 后可补齐。
- runner 镜像需要单独提供，本次只完成编排层。
## 私有 Runner 镜像拉取 Secret

当 `PYCLAW_SANDBOX_RUNNER_IMAGE` 指向阿里云私有 ACR 时，runner Pod 所在的用户 namespace 也必须存在镜像拉取 Secret。

本次补充逻辑：

```text
创建或确保用户 namespace
  -> 如果 PYCLAW_SANDBOX_IMAGE_PULL_SECRET_NAME 非空
  -> 从 PYCLAW_SANDBOX_IMAGE_PULL_SECRET_SOURCE_NAMESPACE 读取同名 Secret
  -> 复制到 pyclaw-user-{userId} namespace
  -> runner Deployment 通过 imagePullSecrets 引用该 Secret
```

推荐 ECS 私有 values：

```yaml
env:
  PYCLAW_SANDBOX_IMAGE_PULL_SECRET_NAME: aliyun-acr-pull-secret
  PYCLAW_SANDBOX_IMAGE_PULL_SECRET_SOURCE_NAMESPACE: pyclaw
```

如果不配置 `PYCLAW_SANDBOX_IMAGE_PULL_SECRET_SOURCE_NAMESPACE`，后端会优先使用 Spring Backend Pod 自己所在的 namespace。Helm Deployment 已通过 Downward API 注入 `POD_NAMESPACE`。

源 Secret 需要先存在，例如当前主应用镜像已使用的：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw get secret aliyun-acr-pull-secret
```

## Runner 镜像发布到阿里云 ACR

runner 镜像应单独构建并推送到 ACR，例如：

```bash
REGISTRY=crpi-li78f6lp5zheaj11.cn-shenzhen.personal.cr.aliyuncs.com
NAMESPACE=pyclaw
IMAGE=sandbox-runner
TAG=<commit-sha-or-version>

docker login $REGISTRY
docker build -t $REGISTRY/$NAMESPACE/$IMAGE:$TAG -f sandbox-runner/Dockerfile .
docker push $REGISTRY/$NAMESPACE/$IMAGE:$TAG
```

推送完成后，在 ECS 私有 `spring-values-k3s.yaml` 中配置：

```yaml
env:
  PYCLAW_SANDBOX_ENABLED: "true"
  PYCLAW_SANDBOX_RUNNER_IMAGE: "crpi-li78f6lp5zheaj11.cn-shenzhen.personal.cr.aliyuncs.com/pyclaw/sandbox-runner:<tag>"
  PYCLAW_SANDBOX_IMAGE_PULL_SECRET_NAME: aliyun-acr-pull-secret
  PYCLAW_SANDBOX_IMAGE_PULL_SECRET_SOURCE_NAMESPACE: pyclaw
```

之后升级 Spring Backend：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install spring-backend ./spring-backend/helm \
  -n pyclaw \
  -f spring-values-k3s.yaml
```

验证用户 namespace 是否自动获得 Secret：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> get secret aliyun-acr-pull-secret
```

验证 runner 是否能拉取镜像：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> get pods
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> describe pod <sandbox-runner-pod>
```