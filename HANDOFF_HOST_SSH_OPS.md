# pyclaw Host SSH Ops 交接文档

更新时间：2026-07-12

## 当前目标

让 `pyclaw` API Pod 可以通过 SSH 以受限用户 `pyclaw-ops` 登录 ECS 宿主机，为后续实现只读宿主机工具打基础，例如：

- `host_uname`
- `host_df`
- `host_free`

安全边界已确定：

- 不运行 privileged Pod。
- 不挂载宿主机 `/`。
- 不挂载 Docker/containerd socket。
- 不暴露通用 `host_shell(command)`。
- 通过 Secret 挂载 SSH 私钥和 `known_hosts`。
- 先做只读工具，后续高风险操作再走审批。

## 已完成的代码修改

### 1. 路由逻辑

文件：

- `openclaw/routing/resolve_route.py`
- `tests/test_routing.py`

已将 `Mention Aliases` 和 `Command Prefixes` 的路由关系从“同时满足”改成“满足任意一个即可”：

- `@开发小助手` 可以触发。
- `开发小助手 ...` 也可以触发。

测试：

```bash
python -m unittest tests.test_routing
```

此前已通过。

### 2. Helm chart 支持 Host SSH Secret

文件：

- `helm/pyclaw/values.yaml`
- `helm/pyclaw/templates/deployment.yaml`

新增 values：

```yaml
hostSsh:
  enabled: false
  existingSecret: pyclaw-host-ssh-secret
  mountPath: /var/run/secrets/pyclaw-host-ssh
```

当 `hostSsh.enabled=true` 时，Deployment 会注入：

```text
HOST_SSH_KEY_PATH=/var/run/secrets/pyclaw-host-ssh/id_ed25519
HOST_SSH_KNOWN_HOSTS_PATH=/var/run/secrets/pyclaw-host-ssh/known_hosts
HOST_SSH_HOST=<from secret host>
HOST_SSH_PORT=<from secret port>
HOST_SSH_USERNAME=<from secret username>
```

并挂载 Secret：

```text
/var/run/secrets/pyclaw-host-ssh
```

### 3. Dockerfile 安装 SSH 客户端

文件：

- `Dockerfile`

已添加 `openssh-client` 安装，使容器内存在：

```text
/usr/bin/ssh
```

### 4. GitHub Actions 保留 Host SSH 配置

文件：

- `.github/workflows/deploy-pyclaw-api.yml`

已在 pyclaw API 的 Helm 部署命令里追加：

```bash
--set hostSsh.enabled=true \
--set hostSsh.existingSecret=pyclaw-host-ssh-secret \
--set hostSsh.mountPath=/var/run/secrets/pyclaw-host-ssh
```

作用：后续 GitHub Actions 自动部署新镜像时，不会把 `HOST_SSH_*` 环境变量和 `/var/run/secrets/pyclaw-host-ssh` Secret 挂载覆盖掉。

## ECS/K3s 当前状态

ECS 项目目录：

```bash
/opt/pyclaw
```

K3s kubectl：

```bash
/usr/local/bin/k3s kubectl
```

Kubeconfig：

```bash
/etc/rancher/k3s/k3s.yaml
```

ECS 上真实 values 文件包括：

```text
/opt/pyclaw/pyclaw-values-k3s.yaml
/opt/pyclaw/pyclaw-api-ingressqueue-mysql-values-k3s.yaml
/opt/pyclaw/spring-values-k3s.yaml
/opt/pyclaw/pyclaw-mysql-values-k3s.yaml
```

注意：这些文件可能包含真实生产信息，不要删除。不要使用 `git clean -fd`。

`/opt/pyclaw/pyclaw-values-k3s.yaml` 已包含：

```yaml
hostSsh:
  enabled: true
  existingSecret: pyclaw-host-ssh-secret
  mountPath: /var/run/secrets/pyclaw-host-ssh
```

`/opt/pyclaw/pyclaw-api-ingressqueue-mysql-values-k3s.yaml` 没有 `hostSsh`，不会覆盖它。

## ECS 上已创建的 SSH 用户和 Secret

宿主机用户：

```text
pyclaw-ops
```

已创建：

```text
/home/pyclaw-ops/.ssh/authorized_keys
```

本地/部署用私钥曾保存到 ECS：

```text
/opt/pyclaw/pyclaw_host_ops_ed25519
```

当前权限已建议设置为：

```bash
chmod 600 /opt/pyclaw/pyclaw_host_ops_ed25519
chmod 644 /opt/pyclaw/known_hosts
```

`known_hosts` 已重新生成，内容应类似：

```text
8.135.60.136 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILbzwrNuQdt4El0UZr1q5pfa7CG4rtyDQC8wAXbU4CAq
```

K3s Secret：

```text
namespace: pyclaw
name: pyclaw-host-ssh-secret
```

包含 keys：

```text
id_ed25519
known_hosts
host
port
username
```

更新 Secret 的命令：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw create secret generic pyclaw-host-ssh-secret \
  --from-file=id_ed25519=/opt/pyclaw/pyclaw_host_ops_ed25519 \
  --from-file=known_hosts=/opt/pyclaw/known_hosts \
  --from-literal=host=8.135.60.136 \
  --from-literal=port=22 \
  --from-literal=username=pyclaw-ops \
  --dry-run=client -o yaml | sudo /usr/local/bin/k3s kubectl apply -f -
```

## 最近验证进度

进入主 Pod：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw exec -it deploy/pyclaw -- sh
```

曾验证到：

```sh
env | grep HOST_SSH
```

输出：

```text
HOST_SSH_PORT=22
HOST_SSH_USERNAME=pyclaw-ops
HOST_SSH_KNOWN_HOSTS_PATH=/var/run/secrets/pyclaw-host-ssh/known_hosts
HOST_SSH_KEY_PATH=/var/run/secrets/pyclaw-host-ssh/id_ed25519
HOST_SSH_HOST=8.135.60.136
```

Secret 挂载也正常：

```sh
ls -l /var/run/secrets/pyclaw-host-ssh
cat "$HOST_SSH_KNOWN_HOSTS_PATH"
```

`known_hosts` 内容已正确显示 `8.135.60.136 ssh-ed25519 ...`。

但随后执行 SSH 测试时出现：

```text
sh: 4: ssh: not found
```

原因：一次手动 Helm upgrade 没带镜像 tag，Deployment 镜像回退到了 values 中的 `pyclaw-api:0.1.0`，该镜像可能不含 `openssh-client`。安装了 SSH 客户端的新镜像 tag 是：

```text
4452700
```

需要重新部署 API 镜像并保留 hostSsh 设置。

## 当前可能卡住的位置

用户执行：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw rollout status deploy/pyclaw
```

显示：

```text
Waiting for deployment "pyclaw" rollout to finish: 1 old replicas are pending termination...
```

这通常只是旧 Pod 正在退出。可按 `Ctrl+C` 停止等待命令，不会中断部署。

下一步先看 Pod：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw get pods -o wide
```

如果新 `pyclaw-...` 是 `Running 1/1`，旧 Pod 是 `Terminating`，可以等 1-2 分钟。

如果长时间卡住：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw describe pod <旧pod名>
sudo /usr/local/bin/k3s kubectl -n pyclaw get events --sort-by=.metadata.creationTimestamp | tail -50
```

## 建议的下一步部署命令

在 ECS 上执行：

```bash
cd /opt/pyclaw

sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install pyclaw ./helm/pyclaw \
  -n pyclaw \
  --create-namespace \
  -f pyclaw-values-k3s.yaml \
  -f pyclaw-api-ingressqueue-mysql-values-k3s.yaml \
  --set-string image.repository=crpi-li78f6lp5zheaj11.cn-shenzhen.personal.cr.aliyuncs.com/pyclaw/pyclaw-api \
  --set-string image.tag=4452700 \
  --set hostSsh.enabled=true \
  --set hostSsh.existingSecret=pyclaw-host-ssh-secret \
  --set hostSsh.mountPath=/var/run/secrets/pyclaw-host-ssh
```

然后验证：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw rollout status deploy/pyclaw
sudo /usr/local/bin/k3s kubectl -n pyclaw exec -it deploy/pyclaw -- sh
```

容器内：

```sh
which ssh
env | grep HOST_SSH
cat "$HOST_SSH_KNOWN_HOSTS_PATH"
ssh -i "$HOST_SSH_KEY_PATH" \
  -o BatchMode=yes \
  -o UserKnownHostsFile="$HOST_SSH_KNOWN_HOSTS_PATH" \
  -o StrictHostKeyChecking=yes \
  -p "$HOST_SSH_PORT" \
  "$HOST_SSH_USERNAME@$HOST_SSH_HOST" \
  -- whoami
```

期望输出：

```text
/usr/bin/ssh
pyclaw-ops
```

## 另一个已知问题

`pyclaw-channel-worker` 曾出现：

```text
0/1 Error
```

主 Pod 的 Host SSH 验证完成后再处理它。查看日志：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw logs deploy/pyclaw-channel-worker --tail=100
```

## 后续代码实现方向

Host SSH 通路验证成功后，进入代码实现：

1. 实现 `HostSshClient`。
2. 从环境变量读取：
   - `HOST_SSH_HOST`
   - `HOST_SSH_PORT`
   - `HOST_SSH_USERNAME`
   - `HOST_SSH_KEY_PATH`
   - `HOST_SSH_KNOWN_HOSTS_PATH`
3. 使用 `asyncio.create_subprocess_exec` 调用 `ssh`，不要拼接 shell 字符串。
4. 只注册白名单只读工具：
   - `host_uname`: `uname -a`
   - `host_df`: `df -h`
   - `host_free`: `free -h`
5. 不提供任意命令执行工具。
6. 工具权限接入现有 `ToolPolicy`，可先通过 allowlist 暴露给指定 Agent。

参考文档：

```text
docs/s02_tool_use/pyclaw-host-ssh-ops-technical-design.md
```

## 建议 commit 消息

```text
feat: 支持 pyclaw API 挂载宿主机 SSH Secret
```

