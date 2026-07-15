# Sandbox 代理 JSON 响应与 Workspace 文件页修复方案

> 日期：2026-07-15  
> 面向执行者：Claude Code  
> 目标：修复前端误判 `Sandbox Down`、Workspace 文件列表误报失败、文件保存请求格式不匹配等问题，确保 Spring Backend 的 sandbox 代理接口与前端 `api/client.js`、sandbox-runner FastAPI 响应格式一致。

## 1. 问题现象

当前 ECS 验证结果：

```text
sandbox-runner Pod: Running
sandbox-runner /healthz: 返回 200 OK
Spring Backend: 已升级到 b8311ea
PYCLAW_SANDBOX_ENABLED=true
Claw ID 与 sandbox-runner Service 名一致
```

浏览器 DevTools 中看到：

```text
GET /api/claws/05940758-cb56-4813-80ea-aeb01b5d1457/sandbox/healthz
Status Code: 200 OK
Response Body:
{"status":"ok","service":"pyclaw-sandbox-runner","clawId":"05940758-cb56-4813-80ea-aeb01b5d1457","ownerUserId":"97b9b2a9-3165-46ac-942d-e47b4b352530"}
Content-Type: text/plain;charset=UTF-8
```

前端显示：

```text
Sandbox 状态: Down
获取文件列表失败: {"path":".","items":[]}
```

## 2. 根因判断

### 2.1 Sandbox 实际没有 Down

`/sandbox/healthz` 已经返回：

```text
200 OK
{"status":"ok", ...}
```

说明 Spring 已经能代理到 sandbox-runner，runner 自身也是健康的。

页面显示 `Down` 是前端解析失败导致的误判。

### 2.2 后端 sandbox 代理返回 text/plain

当前代码位置：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/sandbox/SandboxController.java
```

当前返回方式：

```java
return ResponseEntity.ok(result);
```

因为返回类型是 `ResponseEntity<String>`，Spring 可能把响应头设为：

```text
Content-Type: text/plain;charset=UTF-8
```

但前端统一 API client：

```text
pyclaw-web/src/api/client.js
```

要求所有响应必须是 JSON：

```js
const contentType = res.headers.get("content-type") || "";
if (!contentType.includes("application/json")) {
  const text = await res.text();
  throw new Error(text.slice(0, 200) || `Server returned ${res.status} (non-JSON)`);
}
```

所以即使 HTTP 200 且 body 是合法 JSON，只要 `Content-Type` 是 `text/plain`，前端也会 throw，导致 `Sandbox Down`。

### 2.3 Workspace 文件列表响应结构不匹配

`sandbox-runner` 当前返回：

```json
{
  "path": ".",
  "items": []
}
```

代码位置：

```text
sandbox-runner/app/main.py
```

但当前前端 `WorkspaceFilesPage.vue` 只处理数组：

```js
if (Array.isArray(data)) {
  entries.value = data.map(...)
} else {
  entries.value = []
}
```

同时当前前端判断目录字段使用：

```js
isDir: typeof e === "object" ? e.is_dir : false
```

但 runner 返回的是：

```json
{"type":"directory"}
```

不是：

```json
{"is_dir": true}
```

因此需要适配：

```text
data.items
entry.type === "directory"
```

### 2.4 文件读取响应结构也不匹配

runner 读取文件返回：

```json
{
  "path": "hello.txt",
  "content": "hello pyclaw"
}
```

当前前端：

```js
fileContent.value = typeof data === "string" ? data : JSON.stringify(data);
```

这会把编辑框内容变成整个 JSON，而不是文件内容。

应改成：

```js
fileContent.value = data && typeof data === "object" && "content" in data
  ? data.content
  : String(data ?? "");
```

### 2.5 文件保存请求结构可能不匹配

runner 写文件接口定义：

```python
class WriteFileRequest(BaseModel):
    content: str

@app.put("/v1/workspace/files/{file_path:path}")
def write_file(file_path: str, request: WriteFileRequest):
```

也就是说 runner 期望收到：

```json
{"content":"hello"}
```

当前前端调用：

```js
await api.put(`/api/claws/${clawId.value}/sandbox/files/${encodeURIComponent(filePath)}`, fileContent.value);
```

`api.put` 会做：

```js
body: JSON.stringify(body)
```

如果 body 是字符串，最终请求体是 JSON 字符串字面量：

```json
"hello"
```

而不是：

```json
{"content":"hello"}
```

当前 Spring Controller 又是：

```java
@RequestBody String content
```

容易把 JSON 字符串原样转发给 runner，导致 runner 解析失败。

## 3. 修复目标

修复后必须满足：

```text
1. /sandbox/healthz 返回 application/json。
2. /sandbox/workspace 返回 application/json。
3. /sandbox/files 返回 application/json。
4. /sandbox/files/{filePath} 返回 application/json。
5. PUT /sandbox/files/{filePath} 接收 {"content":"..."}，并转发给 runner 同样结构。
6. 前端 Workspace 文件页正确解析 {path, items}。
7. 空目录显示“目录为空”，不能显示“获取文件列表失败”。
8. 文件读取时编辑框只显示 content 字段。
9. 保存文件时写入真实内容，不额外带 JSON 引号。
10. Sandbox 状态应显示 Healthy，而不是 Down。
```

## 4. 后端修复方案

### 4.1 修改 SandboxController 返回 JSON Content-Type

文件：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/sandbox/SandboxController.java
```

新增 import：

```java
import org.springframework.http.MediaType;
```

新增 helper：

```java
private ResponseEntity<String> jsonOk(String body) {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
}

private ResponseEntity<String> jsonError(HttpStatus status, String message) {
    String safe = message == null ? "" : message
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ");
    return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\":\"" + safe + "\"}");
}
```

然后把成功响应改成：

```java
return jsonOk(result);
```

把错误响应改成：

```java
return jsonError(HttpStatus.BAD_GATEWAY, e.getMessage());
```

把 413 响应也改成：

```java
return jsonError(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds max size: " + MAX_FILE_BYTES + " bytes");
```

涉及方法：

```text
healthz
workspace
listFiles
getFile
putFile
```

### 4.2 更推荐：用 ObjectMapper 返回 JsonNode

如果希望更规范，建议不要手拼 JSON。可以注入 `ObjectMapper`：

```java
private final ObjectMapper objectMapper;
```

将 runner 返回的 JSON 字符串转换成：

```java
JsonNode json = objectMapper.readTree(result);
return ResponseEntity.ok(json);
```

错误响应用：

```java
return ResponseEntity.status(status).body(Map.of("error", message));
```

这种方式更安全，也能避免转义遗漏。

推荐优先采用 ObjectMapper 方案。

### 4.3 修改 PUT 文件请求 DTO

新增内部 record 或单独文件：

```java
public record SandboxWriteFileRequest(String content) {}
```

将 Controller 方法改为：

```java
@PutMapping("/files/{filePath}")
@PreAuthorize("hasAuthority('claw:update')")
public ResponseEntity<?> putFile(
        @PathVariable String clawId,
        @PathVariable String filePath,
        @RequestBody SandboxWriteFileRequest request,
        Authentication authentication) {
    String content = request == null || request.content() == null ? "" : request.content();
    ...
    String result = sandboxClient.putFile(claw.getOwnerUserId(), clawId, filePath, content);
    return jsonOkOrJsonNode(result);
}
```

### 4.4 修改 SandboxClient.putFile 转发 JSON 对象

文件：

```text
spring-backend/src/main/java/com/anxin/pyclaw/backend/sandbox/SandboxClient.java
```

新增 import：

```java
import java.util.Map;
import org.springframework.http.MediaType;
```

修改：

```java
public String putFile(String userId, String clawId, String filePath, String content) {
    String url = serviceUrl(userId, clawId) + "/v1/workspace/files/" + filePath;
    return sandboxCall("putFile", url, () ->
            restClient.put().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content == null ? "" : content))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new SandboxClientException("runner put file failed: status=" + res.getStatusCode());
                    })
                    .body(String.class));
}
```

### 4.5 修复 URL path/query 编码

当前代码：

```java
String url = serviceUrl(userId, clawId) + "/v1/workspace/files?path=" + safePath;
String url = serviceUrl(userId, clawId) + "/v1/workspace/files/" + filePath;
```

如果路径包含空格、`#`、中文、`?` 等字符，可能出错。

建议使用 `UriComponentsBuilder`：

```java
import org.springframework.web.util.UriComponentsBuilder;
```

listFiles：

```java
String url = UriComponentsBuilder
        .fromHttpUrl(serviceUrl(userId, clawId) + "/v1/workspace/files")
        .queryParam("path", safePath)
        .build()
        .toUriString();
```

getFile / putFile 注意 `filePath` 可能包含 `/`，不要简单 `encodeURIComponent` 后又让 Spring `@PathVariable` 截断。后端当前 mapping 是：

```java
@GetMapping("/files/{filePath}")
```

这只能匹配单段路径。要支持子目录，改成：

```java
@GetMapping("/files/{*filePath}")
@PutMapping("/files/{*filePath}")
```

或改成 query 参数形式更简单：

```text
GET /api/claws/{clawId}/sandbox/file?path=dir/a.txt
PUT /api/claws/{clawId}/sandbox/file?path=dir/a.txt
```

短期如果只支持根目录文件，可以暂不改；但建议这次一起改为 query 参数，避免后续踩坑。

## 5. 前端修复方案

### 5.1 修改 WorkspaceFilesPage.vue 解析文件列表

文件：

```text
pyclaw-web/src/views/WorkspaceFilesPage.vue
```

当前：

```js
if (Array.isArray(data)) {
  entries.value = data.map(...)
} else {
  entries.value = [];
}
```

改成：

```js
const rawItems = Array.isArray(data) ? data : (Array.isArray(data?.items) ? data.items : []);
entries.value = rawItems.map(e => ({
  name: typeof e === "string" ? e : e.name || e.path || "",
  path: typeof e === "object" ? e.path || e.name || "" : e,
  isDir: typeof e === "object" ? (e.type === "directory" || e.is_dir === true || e.isDir === true) : false,
  size: typeof e === "object" ? e.size : null,
})).filter(e => e.name);
```

如果 `data.path` 存在，可以同步：

```js
if (data?.path) currentPath.value = data.path;
```

但注意 `navigateTo` 已经更新 currentPath，避免重复跳动。

### 5.2 修改进入子目录逻辑

当前：

```js
@click="entry.isDir ? navigateTo(entry.name) : openFile(entry.name)"
```

建议改成使用 entry.path：

```vue
@click="entry.isDir ? navigateTo(entry) : openFile(entry)"
```

```js
function navigateTo(entry) {
  const newPath = entry.path || (currentPath.value === "." ? entry.name : currentPath.value + "/" + entry.name);
  currentPath.value = newPath;
  loadDir(newPath);
}
```

### 5.3 修改文件读取逻辑

当前：

```js
fileContent.value = typeof data === "string" ? data : JSON.stringify(data);
```

改成：

```js
fileContent.value = data && typeof data === "object" && "content" in data
  ? data.content
  : (typeof data === "string" ? data : JSON.stringify(data, null, 2));
```

`selectedFile` 建议保存 path：

```js
selectedFile.value = filePath;
```

### 5.4 修改文件保存逻辑

当前：

```js
await api.put(`/api/claws/${clawId.value}/sandbox/files/${encodeURIComponent(filePath)}`, fileContent.value);
```

改成：

```js
await api.put(`/api/claws/${clawId.value}/sandbox/files/${encodeURIComponent(filePath)}`, {
  content: fileContent.value,
});
```

如果后端改成 query 参数接口，则改成：

```js
await api.put(`/api/claws/${clawId.value}/sandbox/file?path=${encodeURIComponent(filePath)}`, {
  content: fileContent.value,
});
```

## 6. ClawDetailPage 状态显示修复

文件：

```text
pyclaw-web/src/views/ClawDetailPage.vue
```

当前逻辑是：

```js
try {
  await api.get(`/api/claws/${route.params.id}/sandbox/healthz`);
  sandboxHealthy.value = true;
} catch {
  sandboxHealthy.value = false;
}
```

后端 Content-Type 修复后，这段应该自然恢复。

建议增强错误可观测性：

```js
try {
  await api.get(`/api/claws/${route.params.id}/sandbox/healthz`);
  sandboxHealthy.value = true;
  sandboxError.value = "";
} catch (e) {
  sandboxHealthy.value = false;
  sandboxError.value = e.message || "Sandbox unavailable";
}
```

但页面上是否展示具体错误需谨慎，避免泄露内部 service URL。普通用户可显示短消息，控制台可 `console.warn`。

## 7. 后端测试建议

新增或修改测试：

```text
spring-backend/src/test/java/com/anxin/pyclaw/backend/sandbox/SandboxControllerTest.java
spring-backend/src/test/java/com/anxin/pyclaw/backend/sandbox/SandboxClientTest.java
```

至少覆盖：

```text
GET /sandbox/healthz 返回 Content-Type application/json
GET /sandbox/workspace 返回 Content-Type application/json
GET /sandbox/files 返回 Content-Type application/json
runner 返回 {"path":".","items":[]} 时原样 JSON 返回
runner 异常时返回 application/json 且 status=502
PUT /sandbox/files 接收 {"content":"hello"}
PUT 转发给 runner 的 body 是 {"content":"hello"}，不是 "hello"
普通用户不能访问其他用户 Claw 的 sandbox
```

如果使用 MockMvc，断言示例：

```java
.andExpect(status().isOk())
.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
```

## 8. 前端测试建议

如果当前前端测试框架可用，给 `WorkspaceFilesPage` 增加测试：

```text
响应 {path:".",items:[]} 时显示“目录为空”
响应 {items:[{name:"src",type:"directory"}]} 时显示目录图标并可进入
响应 {path:"hello.txt",content:"hello"} 时编辑框显示 hello
保存文件时请求 body 为 {content:"hello"}
```

如果没有测试框架，至少手工验证。

## 9. ECS 验收步骤

### 9.1 确认 Spring 和 Web 镜像版本

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw get deployment pyclaw-spring-backend \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'

sudo /usr/local/bin/k3s kubectl -n pyclaw get deployment pyclaw-web \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

### 9.2 验证 runner 自身

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-97b9b2a9-3165-46ac-942d-e47b4b352530 exec deploy/sandbox-runner-05940758-cb56-4813-80ea-aeb01b5d1457 -- \
  python -c 'import urllib.request; print(urllib.request.urlopen("http://127.0.0.1:8000/healthz").read().decode())'
```

### 9.3 验证 Spring API 响应头

用浏览器 DevTools 或 curl 验证：

```bash
curl -i "https://api.anxin-hitsz.com/api/claws/05940758-cb56-4813-80ea-aeb01b5d1457/sandbox/healthz" \
  -H "Authorization: Bearer <USER_TOKEN>"
```

必须看到：

```text
HTTP/2 200
content-type: application/json
```

响应体：

```json
{"status":"ok","service":"pyclaw-sandbox-runner",...}
```

### 9.4 验证文件列表

```bash
curl -i "https://api.anxin-hitsz.com/api/claws/05940758-cb56-4813-80ea-aeb01b5d1457/sandbox/files?path=." \
  -H "Authorization: Bearer <USER_TOKEN>"
```

必须看到：

```text
content-type: application/json
```

响应体可以是空目录：

```json
{"path":".","items":[]}
```

前端应该显示：

```text
目录为空
```

不能显示：

```text
获取文件列表失败
```

### 9.5 验证写入文件

在 Web 页面新增或保存文件后，进 runner 验证：

```bash
sudo /usr/local/bin/k3s kubectl -n pyclaw-user-97b9b2a9-3165-46ac-942d-e47b4b352530 exec deploy/sandbox-runner-05940758-cb56-4813-80ea-aeb01b5d1457 -- \
  python -c 'from pathlib import Path; print(Path("/workspace/hello.txt").read_text())'
```

## 10. 推荐提交拆分

```text
fix: 修复 sandbox 代理 JSON 响应类型
fix: 修复 Workspace 文件页解析 runner 响应
fix: 修复 sandbox 文件保存请求格式
test: 增加 sandbox 代理与文件页回归测试
```

## 11. Definition of Done

完成标准：

```text
1. DevTools 中 /sandbox/healthz 为 200 且 Content-Type 是 application/json。
2. ClawDetail 显示 Sandbox Health = Healthy。
3. Workspace 文件页空目录显示“目录为空”。
4. 文件列表能展示 runner 返回的 items。
5. 文件读取只显示 content，不显示整个 JSON。
6. 文件保存后 runner /workspace 内文件内容正确。
7. Spring 日志无 sandbox 代理异常。
8. 普通用户仍不能访问其他用户 Claw 的 sandbox 接口。
```