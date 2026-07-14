# 最小 Sandbox Runner 镜像实现记录

> 日期：2026-07-14
> 目标：提供一个可以被 Claw 沙箱编排拉起的最小可执行 runner 镜像。

## 目录

```text
sandbox-runner/
  Dockerfile
  requirements.txt
  app/main.py
```

## 当前能力

最小 runner 是一个 FastAPI 服务，监听 8000 端口。

接口：

```text
GET /healthz
GET /v1/workspace
GET /v1/workspace/files?path=.
GET /v1/workspace/files/{file_path}
PUT /v1/workspace/files/{file_path}
```

设计边界：

- 只操作 `/workspace` 内文件。
- 防止 `../` 路径逃逸 workspace。
- 默认非 root 用户 `runner` 运行。
- 暂不开放通用 shell 命令执行。
- 后续工具执行能力应通过白名单 API 增加，而不是直接暴露任意 shell。

## 本地构建

在项目根目录执行：

```bash
docker build -t pyclaw/sandbox-runner:local -f sandbox-runner/Dockerfile sandbox-runner
```

本地运行：

```bash
docker run --rm -p 8000:8000 \
  -e PYCLAW_CLAW_ID=demo-claw \
  -e PYCLAW_OWNER_USER_ID=demo-user \
  -e PYCLAW_CLAW_NAME=demo \
  pyclaw/sandbox-runner:local
```

验证：

```bash
curl http://127.0.0.1:8000/healthz
curl http://127.0.0.1:8000/v1/workspace
```

写入文件：

```bash
curl -X PUT http://127.0.0.1:8000/v1/workspace/files/hello.txt \
  -H 'Content-Type: application/json' \
  -d '{"content":"hello pyclaw"}'
```

读取文件：

```bash
curl http://127.0.0.1:8000/v1/workspace/files/hello.txt
```

## 推送到阿里云 ACR

手动推送：

```bash
REGISTRY=crpi-li78f6lp5zheaj11.cn-shenzhen.personal.cr.aliyuncs.com
NAMESPACE=pyclaw
IMAGE=sandbox-runner
TAG=<commit-sha-or-version>

docker login $REGISTRY
docker build -t $REGISTRY/$NAMESPACE/$IMAGE:$TAG -f sandbox-runner/Dockerfile sandbox-runner
docker push $REGISTRY/$NAMESPACE/$IMAGE:$TAG
```

GitHub Actions：

新增 workflow：

```text
.github/workflows/build-sandbox-runner.yml
```

触发方式：

- push 到 main，且改动 `sandbox-runner/**`
- 手动 Run workflow

需要 GitHub Secrets：

```text
ACR_REGISTRY
ACR_USERNAME
ACR_PASSWORD
```

推送后的镜像地址格式：

```text
${ACR_REGISTRY}/pyclaw/sandbox-runner:${GITHUB_SHA}
```

## ECS 启用

在 `/opt/pyclaw/spring-values-k3s.yaml` 中配置：

```yaml
env:
  PYCLAW_SANDBOX_ENABLED: "true"
  PYCLAW_SANDBOX_RUNNER_IMAGE: "crpi-li78f6lp5zheaj11.cn-shenzhen.personal.cr.aliyuncs.com/pyclaw/sandbox-runner:<tag>"
  PYCLAW_SANDBOX_IMAGE_PULL_SECRET_NAME: "aliyun-acr-pull-secret"
  PYCLAW_SANDBOX_IMAGE_PULL_SECRET_SOURCE_NAMESPACE: "pyclaw"
```

重新部署 Spring Backend：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install spring-backend ./spring-backend/helm \
  -n pyclaw \
  -f spring-values-k3s.yaml
```

创建 Claw 后验证：

```bash
sudo /usr/local/bin/k3s kubectl get ns | grep pyclaw-user
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> get deploy,svc,pvc,pods,secrets
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> logs deployment/sandbox-runner-<clawId> --tail=100
```

如果 runner Pod 出现 `ImagePullBackOff`，优先检查：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> get secret aliyun-acr-pull-secret
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-<userId> describe pod <runner-pod>
```
