# Claude Code 工作规约

重构项目时，先阅读以下文档：

```text
docs/architecture/ARCHITECTURE.md
docs/plans/migration-plan.md
docs/architecture/backend-coding-standards.md
docs/architecture/runtime-coding-standards.md
```

工作要求：

```text
1. 每次只迁移一个服务或一个明确功能。
2. 按 migration-plan.md 的阶段推进。
3. 按 backend-coding-standards.md 编写 Spring Boot 代码。
4. 按 runtime-coding-standards.md 编写 FastAPI Runtime 代码。
5. Spring Boot Controller 不写业务逻辑。
6. FastAPI router 不写业务编排。
7. Claw Runner 必须保持执行环境隔离。
8. 不直接把 Entity 暴露给前端或跨服务接口。
9. 不把构建产物、IDE 临时文件、本地 Maven 仓库纳入提交。
10. 改动后运行对应模块的校验或测试。
```

当前优先级以 `docs/plans/migration-plan.md` 为准。
