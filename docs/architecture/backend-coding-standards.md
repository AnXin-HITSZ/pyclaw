# 后端代码规约

## 适用范围

本规约适用于 `backend/` 下所有 Spring Boot 服务：

```text
gateway
backend-for-frontend
agent-marketplace-service
conversation-service
runtime-service
billing-service
```

## 基本原则

```text
1. Controller 只处理 HTTP 层职责，不写业务逻辑。
2. 业务逻辑必须进入 service / impl。
3. Repository 只处理持久化，不编排业务流程。
4. Client 只封装服务间调用、Runtime API 调用和第三方调用。
5. DTO 用于接口入参和出参，不直接暴露 Entity。
6. Domain 承载领域对象、状态、枚举和值对象。
7. 每个服务保持独立启动和独立测试。
```

## 标准包结构

每个 Spring Boot 服务默认采用以下结构：

```text
src/main/java/com/clawsaas/<service>/
  <Service>Application.java

  controller/
  service/
  service/impl/
  repository/
  domain/
  dto/
  client/
  config/
  exception/
```

可选目录：

```text
mapper/
  DTO、Entity、Domain 对象之间的转换。

constant/
  服务内常量。

event/
  领域事件或应用事件。
```

## Controller 规约

Controller 负责 HTTP 接口适配。

允许：

```text
接收请求参数
做轻量参数校验
读取用户上下文
调用 service
返回 response DTO
```

禁止：

```text
写业务流程
直接访问 repository
直接调用其他微服务
直接调用 pyclaw-runtime-api
直接返回 Entity
处理复杂事务
```

命名：

```text
ConversationController
RuntimeRunController
AgentMarketplaceController
BillingController
```

示例结构：

```java
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }
}
```

## Service 规约

`service/` 放业务用例接口，方法名表达业务动作。

命名：

```text
ConversationService
RuntimeRunService
AgentPublishService
BillingUsageService
```

方法命名建议：

```text
createConversation
appendMessage
startRun
resumeApproval
publishAgentVersion
recordUsage
```

Service 接口不处理 HTTP 对象，不依赖 `HttpServletRequest`、`ServerWebExchange` 等 Web 类型。

## Impl 规约

`service/impl/` 放业务用例实现。

命名：

```text
ConversationServiceImpl
RuntimeRunServiceImpl
AgentPublishServiceImpl
BillingUsageServiceImpl
```

Impl 负责：

```text
业务流程编排
业务校验
事务边界
调用 repository
调用 client
组装返回 DTO
```

事务建议放在 Impl 方法上：

```java
@Service
public class ConversationServiceImpl implements ConversationService {

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request) {
        // business flow
    }
}
```

## Repository 规约

Repository 负责持久化。

允许：

```text
数据库查询
数据库保存
简单查询条件组合
```

禁止：

```text
调用其他服务
写跨领域业务流程
处理 HTTP 请求
返回前端 Response DTO
```

命名：

```text
ConversationRepository
MessageRepository
AgentRepository
UsageRecordRepository
```

## Domain 规约

`domain/` 放领域对象、枚举、状态和值对象。

示例：

```text
Conversation
Message
ConversationStatus
Agent
AgentVersion
RuntimeRun
RunStatus
BillingPlan
```

领域对象可以承载核心业务规则，例如状态流转判断、额度状态判断、消息类型判断。

## DTO 规约

`dto/` 放接口请求和响应对象。

命名：

```text
CreateConversationRequest
ConversationResponse
StartRunRequest
RuntimeRunResponse
AgentSummaryResponse
BillingUsageResponse
```

要求：

```text
Request / Response 分开。
对外接口不直接返回 Entity。
跨服务接口也使用 DTO。
DTO 不写业务逻辑。
```

## Client 规约

`client/` 放服务间调用、Runtime API 调用和第三方系统调用。

命名：

```text
ConversationServiceClient
RuntimeServiceClient
PyclawRuntimeApiClient
BillingServiceClient
```

要求：

```text
BFF 调用领域服务必须通过 client。
runtime-service 调用 pyclaw-runtime-api 必须通过 client。
client 负责封装 URL、请求、响应和错误转换。
业务判断仍放在 service/impl。
```

## Exception 规约

`exception/` 放服务内异常和统一异常处理。

建议：

```text
BusinessException
NotFoundException
ForbiddenException
ExternalServiceException
GlobalExceptionHandler
```

异常响应应保持结构稳定：

```text
code
message
requestId
details
```

## 配置规约

`config/` 放 Spring 配置。

建议：

```text
WebConfig
HttpClientConfig
SecurityConfig
JacksonConfig
```

配置类不写业务逻辑。

## 服务边界

调用关系：

```text
Frontend
  -> gateway
  -> backend-for-frontend
  -> domain services
  -> database / pyclaw-runtime-api / external systems
```

禁止：

```text
Frontend 直接调用 domain services
Frontend 直接调用 pyclaw-runtime-api
gateway 编排业务
BFF 直接访问其他服务数据库
一个领域服务直接访问另一个领域服务数据库
```

## Gateway 特殊规约

Gateway 使用 Spring Cloud Gateway WebFlux。

允许：

```text
路由转发
CORS
认证前置
限流
请求 ID
访问日志
健康检查
```

禁止：

```text
写业务聚合
访问业务数据库
调用 pyclaw-runtime-api 执行业务流程
```

## BFF 特殊规约

BFF 面向前端页面，不保存领域事实。

允许：

```text
聚合多个服务结果
裁剪前端 Response
页面级权限判断
前端接口版本适配
```

禁止：

```text
替代领域服务保存业务数据
直接访问领域服务数据库
把 Entity 原样返回给前端
```

## 测试规约

每次代码迁移或重构后，至少运行受影响模块的校验：

```text
cd backend
mvn -pl <module> test
```

如果只改 POM 或配置，可以运行：

```text
cd backend
mvn -pl <module> validate
```

跨模块改动需要运行：

```text
cd backend
mvn validate
```

## 迁移旧代码规约

从旧项目 `D:\projects\personal\pyclaw` 迁移时：

```text
1. 先定位旧代码职责。
2. 判断归属模块。
3. 将 HTTP 入口放到 controller。
4. 将业务逻辑放到 service/impl。
5. 将数据库访问放到 repository。
6. 将外部调用放到 client。
7. 将请求响应对象放到 dto。
8. 不保留胖 Controller。
```
