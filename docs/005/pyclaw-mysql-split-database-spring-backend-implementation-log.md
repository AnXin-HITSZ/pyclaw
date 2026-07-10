# PyClaw MySQL 双 Database 与 Spring Backend 接入实现记录

## 1. 目标

在同一个 K3s MySQL 实例中拆分两个 database：

```text
pyclaw_runtime  pyclaw-api / channel-worker 使用，保存 ingress_queue
pyclaw_control  Spring Backend 使用，保存用户、Provider、Channel、Agent、Route、Tool 等控制台配置
```

这样可以继续复用一个 MySQL StatefulSet / Service / PVC，同时把运行态消息队列数据和控制面配置数据隔离开。

## 2. 本次代码改动

### 2.1 MySQL Helm Chart

新增 values：

```yaml
mysql:
  database: pyclaw_runtime
  databases:
    runtime: pyclaw_runtime
    control: pyclaw_control
```

新增 Helm hook Job：

```text
helm/pyclaw-mysql/templates/bootstrap-databases-job.yaml
```

该 Job 在 `post-install` / `post-upgrade` 执行，逻辑是：

```sql
CREATE DATABASE IF NOT EXISTS pyclaw_runtime;
CREATE DATABASE IF NOT EXISTS pyclaw_control;
GRANT ALL PRIVILEGES ON pyclaw_runtime.* TO 'pyclaw'@'%';
GRANT ALL PRIVILEGES ON pyclaw_control.* TO 'pyclaw'@'%';
```

使用 hook 的原因：MySQL 官方镜像的 `/docker-entrypoint-initdb.d` 只会在空数据目录首次初始化时执行，已经运行过的 ECS / PVC 不会再自动执行初始化 SQL。Hook Job 是幂等的，适合已有实例补建 database。

### 2.2 Spring Backend

Spring Backend Helm 默认 datasource 从 H2 文件库切换为 MySQL control database：

```yaml
env:
  SPRING_DATASOURCE_URL: jdbc:mysql://pyclaw-mysql:3306/pyclaw_control?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
  SPRING_DATASOURCE_USERNAME: pyclaw
  SPRING_JPA_DDL_AUTO: update
```

同时关闭 Spring Backend 默认 PVC：

```yaml
persistence:
  enabled: false
```

Spring Backend 状态进入 MySQL 后，不再依赖 `/data/pyclaw-backend.mv.db`。

### 2.3 MySQL JDBC 驱动

Spring Backend 新增运行时依赖：

```xml
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <scope>runtime</scope>
</dependency>
```

否则切换到 `jdbc:mysql://...` 后应用无法加载 MySQL JDBC Driver。

## 3. ECS 更新步骤

### 3.1 更新代码

```bash
cd /opt/pyclaw
git pull --ff-only
```

### 3.2 更新 MySQL Secret 中的默认 database 名称

如果当前 Secret 仍是 `MYSQL_DATABASE=pyclaw`，建议更新为 runtime database：

```bash
MYSQL_ROOT_PASSWORD='<现有 root 密码>'
MYSQL_PASSWORD='<现有 pyclaw 用户密码>'

sudo /usr/local/bin/k3s kubectl -n pyclaw create secret generic pyclaw-mysql-secret \
  --from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  --from-literal=MYSQL_DATABASE="pyclaw_runtime" \
  --from-literal=MYSQL_USER="pyclaw" \
  --from-literal=MYSQL_PASSWORD="$MYSQL_PASSWORD" \
  --dry-run=client -o yaml \
  | sudo /usr/local/bin/k3s kubectl apply -f -
```

注意：对于已经初始化过的 MySQL PVC，修改 `MYSQL_DATABASE` 不会自动迁移旧库；真正补建两个 database 的动作由 Helm hook Job 完成。

### 3.3 升级 MySQL Release

```bash
cd /opt/pyclaw

sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install pyclaw-mysql ./helm/pyclaw-mysql \
  -n pyclaw \
  --create-namespace \
  -f pyclaw-mysql-values-k3s.yaml
```

验证两个 database：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw exec statefulset/pyclaw-mysql -- \
  sh -c 'mysql -h 127.0.0.1 -upyclaw -p"$MYSQL_PASSWORD" -e "show databases;"'
```

期望包含：

```text
pyclaw_runtime
pyclaw_control
```

### 3.4 更新 pyclaw-api / worker 队列 DSN

`OPENCLAW_INGRESS_QUEUE_DSN` 应指向 runtime database：

```bash
DSN="mysql+pymysql://pyclaw:${MYSQL_PASSWORD}@pyclaw-mysql:3306/pyclaw_runtime?charset=utf8mb4&table=ingress_queue"

sudo /usr/local/bin/k3s kubectl -n pyclaw create secret generic pyclaw-ingress-queue-secret \
  --from-literal=OPENCLAW_INGRESS_QUEUE_DSN="$DSN" \
  --dry-run=client -o yaml \
  | sudo /usr/local/bin/k3s kubectl apply -f -
```

如果密码包含 `@`、`:`、`/`、`#`、`?`、`&`、空格等 URL 特殊字符，需要先 URL encode 后再放入 DSN。

重新部署 pyclaw-api 和 worker：

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install pyclaw ./helm/pyclaw \
  -n pyclaw \
  --create-namespace \
  -f pyclaw-values-k3s.yaml \
  -f pyclaw-api-ingressqueue-mysql-values-k3s.yaml
```

### 3.5 更新 Spring Backend Secret

Spring Backend 需要读取同一个 MySQL 用户密码：

```bash
SPRING_DATASOURCE_PASSWORD_B64=$(printf '%s' "$MYSQL_PASSWORD" | base64 -w0)

sudo /usr/local/bin/k3s kubectl -n pyclaw patch secret pyclaw-spring-backend-secret \
  --type merge \
  -p "{\"data\":{\"SPRING_DATASOURCE_PASSWORD\":\"${SPRING_DATASOURCE_PASSWORD_B64}\"}}"
```

保留原有 Secret 里的 `JWT_SIGNING_SECRET`、`BOOTSTRAP_ADMIN_PASSWORD`、`PYCLAW_API_TOKEN`、`PYCLAW_INTERNAL_API_TOKEN`。

### 3.6 升级 Spring Backend

```bash
sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade --install spring-backend ./spring-backend/helm \
  -n pyclaw \
  --create-namespace \
  -f spring-values-k3s.yaml
```

如果你的 release 名是 `pyclaw-spring-backend`，继续使用现有 release 名，不要混用：

```bash
helm -n pyclaw list
```

### 3.7 验证

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw rollout status deployment/pyclaw-spring-backend
sudo /usr/local/bin/k3s kubectl -n pyclaw get endpoints pyclaw-spring-backend
curl -i https://api.anxin-hitsz.com/healthz
```

Spring Backend 日志中应看到：

```text
jdbc:mysql://pyclaw-mysql:3306/pyclaw_control
```

不应再看到：

```text
jdbc:h2:file:/data/pyclaw-backend
```

## 4. 注意事项

1. 旧 H2 文件库中的用户、Provider、Agent、Route 配置不会自动迁移到 MySQL。
2. 首次切换到 `pyclaw_control` 后，Spring Backend 会通过 `SPRING_JPA_DDL_AUTO=update` 自动建表。
3. Bootstrap admin 会重新创建或补齐默认管理员权限。
4. `pyclaw_runtime` 和 `pyclaw_control` 共用一个 MySQL 用户 `pyclaw`，后续生产化可以拆成 `pyclaw_runtime_user` 与 `pyclaw_control_user`。
5. MySQL 密码如果要写入 DSN，建议避免 URL 特殊字符，或使用 URL encode 后的密码。