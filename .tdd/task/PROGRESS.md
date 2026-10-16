# 登录功能 · TDD 进度表

> 87 个任务项，按**里程碑 → 批次**组织。执行顺序见 `../执行清单.md`，不要按编号顺序做。
> 图例：`[ ]` 未开始 · `[x]` 已完成 · **◇** 可选加固项（第一遍可整行跳过）· **★** 高价值断言（建议别跳）
> **纪律：红着的时候不打勾，也不提交。** 打勾的前提是本批次跑全量测试全绿。

**总进度：0 / 87（0%）**

> 说明：B4.0 是纯配置步骤、B4.2 的勾选项是 3.1.2 / 3.1.3 的落地验证，**都不重复计入 87 项**。

| 里程碑 | 批次 | 任务项 | 完成 |
|---|---|---|---|
| M1 值对象 | B1.1~B1.5 | 20 | 0 |
| M2 聚合根 + 事件 | B2.1~B2.2 | 10 | 0 |
| M3 端口 + 应用编排 | B3.1~B3.7 | 34 | 0 |
| M4 infrastructure | B4.0~B4.3 | 9 | 0 |
| M5 adapter/web | B5.1 | 7 | 0 |
| M6 观察者 | B6.1~B6.3 | 7 | 0 |

---

## M1 · 值对象层（纯 domain，零依赖）

> 不用 Spring、不用 Mockito、不用数据库。热身层，用来建立"红绿重构"的节奏感。

### B1.1 · `Username`（功能点 1.1）
- [ ] 1.1.1 `Username` 拒绝 `null`、空串、纯空白串
- [ ] 1.1.2 `Username` 长度边界 4~20（3 抛 / 4 过 / 20 过 / 21 抛）
- [ ] 1.1.3 `Username` 只接受字母、数字、下划线
- [ ] 1.1.4 `Username` 构造时自动 trim
- [ ] 1.1.5 `Username` 具备值语义（equals / hashCode / HashMap key）

### B1.2 · `RawPassword`（功能点 1.2）
- [ ] 1.2.1 `RawPassword` 拒绝 `null` 与空串
- [ ] 1.2.2 `RawPassword` 长度边界 6~32（5 抛 / 6 过 / 32 过 / 33 抛）
- [ ] ◇ 1.2.3 `RawPassword` 无任何修改内部状态的方法（反射）
- [ ] ★ 1.2.4 `RawPassword.toString()` 不得泄露明文

### B1.3 · `PasswordHash`（功能点 4.1）
- [ ] 4.1.1 `PasswordHash` 拒绝 `null` 与空串
- [ ] ◇ 4.1.2 `PasswordHash` 不提供任何返回明文的途径（防倒退设计）
- [ ] ★ 4.1.3 `PasswordHash.toString()` 打码
- [ ] 4.1.4 `PasswordHash` 具备值语义

### B1.4 · `Token`（功能点 5.1）
- [ ] 5.1.1 `Token` 拒绝 `null` 与空串
- [ ] 5.1.2 `Token` 格式为 32 位十六进制
- [ ] ★ 5.1.3 `Token.toString()` 打码，只露前 8 位
- [ ] 5.1.4 `Token` 具备值语义
- [ ] ★ 5.1.5 连续两次生成的 `Token` 不相等（不可预测）

### ◇B1.5 · 架构约束测试（可整批跳过）
- [ ] ◇ 2.1.1 校验全部下沉到值对象，Service 里不存在 `isEmpty` 分支
- [ ] ◇ 3.1.1 `domain.port` 包零框架依赖（`grep` 或 ArchUnit）

---

## M2 · 聚合根与领域事件

### B2.1 · `User` + `UserStatus` + `TimeProvider`（功能点 3.2）
- [ ] 3.2.1 `UserStatus` 只有 `ENABLED` / `DISABLED` 两个取值
- [ ] 3.2.2 `ENABLED` 的账号 `isLoginable()` 为 `true`
- [ ] 3.2.3 `DISABLED` 的账号 `isLoginable()` 为 `false`
- [ ] ★ 3.2.4 `status` 为 `null` 时视为**不可登录**（防御历史数据）
- [ ] 3.2.5 `User` 能从持久化数据重建（`reconstitute` 静态工厂，允许空昵称/头像）
- [ ] ◇ 3.2.6 `User` 不可变、无 setter

### B2.2 · `UserLoggedInEvent`（功能点 6.1）
- [ ] 6.1.1 事件携带 `userId` / `username` / `ip` / `device` / `occurredAt`
- [ ] ◇ 6.1.2 事件对象不可变（字段 `final`、无 setter）
- [ ] 6.1.3 事件中的 `username` 是规范化后的值
- [ ] ★ 6.1.4 事件中的 `occurredAt` 来自注入的 `TimeProvider`

---

## M3 · 端口 + application 编排（Mockito）

> 第一次用 Mockito。节奏是：**先定端口接口 → 再写 Service 用例 → 假实现撑住**。

### B3.1 · 出站端口 + `LoginCommand`（功能点 3.1 / 4.2 / 5.2）
- [ ] 3.1.2 `UserRepository.findByUsername` 接受 `Username` 而非 `String`
- [ ] 3.1.3 `findByUsername` 返回 `Optional<User>`
- [ ] 4.2.1 `PasswordEncoder` 声明 `matches(RawPassword, PasswordHash) -> boolean`
- [ ] 4.2.2 `PasswordEncoder` 签名是领域类型，不出现 `String`
- [ ] 5.2.1 `TokenIssuer` 只声明 `issue(LoginUser) -> Token`
- [ ] 5.2.2 `TokenStore` 声明 `save` / `find` / `remove`，`find` 返回 `Optional`
- [ ] ◇ 5.2.3 `TokenStore` 签名里不出现任何 Redis / Spring 类型

### B3.2 · `LoginApplicationService` 主成功路径（功能点 1.3）
- [ ] 1.3.1 认证通过时 `issue` 恰好调用一次
- [ ] 1.3.2 返回结果业务码 200，`data` 就是令牌字符串
- [ ] 1.3.3 签发给令牌的 `userId` 来自仓储查出的 `User`
- [ ] ★ 1.3.4 主流程不自己写库（`verify(userRepository, never()).save(any())`）

### B3.3 · 入参守卫与副作用隔离（功能点 2.2）
- [ ] 2.2.1 `command` 为 `null` 立即抛异常，不触碰仓储
- [ ] ★ 2.2.2 校验失败不得签发令牌
- [ ] 2.2.3 校验失败不得调用 `PasswordEncoder.matches`
- [ ] ★ 2.2.4 校验失败不得发布事件

### B3.4 · 认证三分支（功能点 3.3）
- [ ] 3.3.1 仓储返回空 → 提示「账号不存在」
- [ ] 3.3.2 密码不匹配 → 提示「密码错误」
- [ ] 3.3.3 账号被禁用 → 提示「账号已被禁用」
- [ ] ★ 3.3.4 三种失败路径都不得签发令牌
- [ ] ★ 3.3.5 三种失败路径都不得发布事件
- [ ] 3.3.6 账号被禁用时不得调用 `matches`
- [ ] ★ 3.3.7 认证必须发生在签发令牌之前（`InOrder`）
- [ ] ◇ 3.3.8 统一失败文案为「账号或密码错误」（安全加固，需求变更演练）

### B3.5 · 密码比对走端口（功能点 4.3）
- [ ] ★ 4.3.1 `matches` 的明文来自命令、哈希来自仓储查出的账号
- [ ] ★ 4.3.2 走端口比对，不自己 `equals`（`times(1)).matches(...)`）
- [ ] 4.3.3 认证失败时 `matches` 恰好调用一次

### B3.6 · 令牌签发时机（功能点 5.3）
- [ ] 5.3.1 认证成功后 `issue` 恰好一次
- [ ] ★ 5.3.2 返回的 token 就是 `issue` 的返回值
- [ ] ★ 5.3.3 签发必须晚于认证（`InOrder`）

### B3.7 · 事件发布时机（功能点 6.2）
- [ ] 6.2.1 认证成功后 `publishEvent` 恰好一次
- [ ] ★ 6.2.2 事件中的 `userId` 不为 `null`，等于查出的账号 id
- [ ] ★ 6.2.3 发布必须晚于认证（`InOrder`）
- [ ] ★ 6.2.4 认证失败不得发布事件
- [ ] ★ 6.2.5 签发令牌抛异常时不得发布事件

---

## M4 · infrastructure（第一次用真东西）

### B4.0 · 加 pom 依赖（无测试，不计入任务项）
- [ ] `mybatis-spring-boot-starter` + `sqlite-jdbc` + `spring-security-crypto` 加完仍全绿

### B4.1 · `BCryptPasswordEncoderAdapter`（功能点 4.4）
- [ ] 4.4.1 同一明文两次 `encode` 结果不同（加盐）
- [ ] 4.4.2 `matches(正确明文, 哈希)` 为 `true`
- [ ] 4.4.3 `matches(错误明文, 哈希)` 为 `false`（不抛异常）
- [ ] 4.4.4 哈希 60 字符、`$2a$` 开头

### B4.2 · `MyBatisUserRepository` + 建表（功能点 3.1 的落地，不重复计入）
- [ ] 仓储能按 `Username` 查到账号并把字段映射完整（含 `status` 为 `NULL` 的历史数据）
- [ ] 查不到时返回 `Optional.empty()`（不是 `null`、不抛异常）
- [ ] `touchLogin` 真跑一次 JDBC：`login_count` 真的 +1、`last_login_time` 真的被写入
- [ ] ⚠️ 数据源指向 `@TempDir` 临时库，**绝不指向 `shop/shop.db`**

### B4.3 · `InMemoryTokenStore`（功能点 5.4）
- [ ] 5.4.1 存入后能按令牌取回同一份身份
- [ ] 5.4.2 取从未签发的令牌 → `Optional.empty()`
- [ ] 5.4.3 `remove` 之后取不回（主动失效）
- [ ] ★ 5.4.4 超过 TTL 后取不回，且不靠 `Thread.sleep`（假时钟）
- [ ] ◇ 5.4.5 换掉 `TokenStore` 实现时，application 层测试一行不改

---

## M5 · adapter/web

### B5.1 · `LoginController` 全量（功能点 1.4 / 2.3 / 3.4）
- [ ] 1.4.1 `POST /auth/login` → HTTP 200 + 业务码 200
- [ ] 1.4.2 JSON 字段正确映射到 `LoginRequest`
- [ ] ◇ 1.4.3 Controller 不做业务判断，原样透传 Service 结果
- [ ] 2.3.1 `LoginRequest` 上有 Bean Validation 必填约束
- [ ] 2.3.2 `GlobalExceptionHandler` 统一转 `ApiResult(500)`
- [ ] 3.4.1 认证失败返回业务码 500 + 明确 `msg`
- [ ] ★ 3.4.2 认证失败的响应体里不出现 `token`

---

## M6 · 观察者（事件监听器）

### B6.1 · `LoginLogListener`（功能点 6.3）
- [ ] 6.3.1 收到事件后插入恰好一条登录日志
- [ ] 6.3.2 日志含 ip 与 device，`userType` 为 `buyer`
- [ ] ★ 6.3.3 写日志失败不影响主流程

### B6.2 · `LoginCountListener`（功能点 6.4）
- [ ] 6.4.1 收到事件后累计登录次数 +1
- [ ] ★ 6.4.2 最后登录时间用事件里的 `occurredAt`，不是 `LocalDateTime.now()`
- [ ] ◇ 6.4.3 两个监听器互不感知（观察者解耦性断言）

### B6.3 · 真实事件机制集成测试
- [ ] ★ 6.4.4 任一监听器抛异常，`login` 仍返回成功

---

## 收尾验收

- [ ] `mvn -B -pl mall-auth test` 全绿
- [ ] `grep -r "^import org.springframework" mall-auth/src/main/java/com/mall/auth/domain` 零结果
- [ ] `git log --oneline` 能看到"一批次一提交"的痕迹
- [ ] 起服务手测：正确密码拿到 token；错误密码返回 500 且响应里没有 token
