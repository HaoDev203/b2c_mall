# b2c_mall — Spring Cloud Alibaba B2C 商城

基于 **Spring Cloud Alibaba** 的 B2C 商城后端项目，采用微服务架构（网关 + 业务服务），
使用 Nacos 做服务注册与配置中心、Gateway 做统一入口与路由、Redis 做令牌存储、SQLite 做数据持久化。

项目在完整实现商城业务功能（登录注册、商品上下架、下单支付、订单流转、后台管理）的同时，
**落地了 5 种经典设计模式**（观察者、模板方法、状态机、策略、状态模式），
每种模式都对应真实业务场景，而非为演示而演示。

> ⚠️ 本项目为**纯后端 API 服务**，不含前端页面。所有接口通过 `api-test.http`（IDEA 内置 HTTP Client）调试。

---

## 一、技术栈

| 分类 | 组件 | 版本 | 用途 |
|---|---|---|---|
| 语言 | Java | 8 | 全部业务代码 |
| 框架 | Spring Boot | 2.7.18 | 基础框架 |
| 微服务 | Spring Cloud | 2021.0.5 | 微服务基础组件 |
| 微服务 | Spring Cloud Alibaba | 2021.0.4.0 | Nacos 集成 |
| 注册/配置 | Nacos | client 1.4.7 | 服务注册发现 + 配置中心 |
| 网关 | Spring Cloud Gateway | 随 SC 版本 | 统一入口、路由转发、前缀剥离 |
| 持久层 | MyBatis | starter 2.3.2 | ORM，注解 + XML 混合 |
| 数据库 | SQLite | jdbc 3.42.0.0 | 轻量文件数据库，免安装 |
| 缓存 | Redis | — | 令牌存储（支持主动失效） |
| 工具 | Lombok | 1.18.30 | 简化实体类 |
| 校验 | spring-boot-starter-validation | 2.7.18 | 请求参数校验 |

### 为什么用 SQLite 而不是 MySQL

课程目标是演示微服务架构与设计模式，不是压测数据库。SQLite 免安装、零配置、可随项目携带，
让评审只需 `git clone` 即可跑起来。建表脚本同时提供了 MySQL 版本（`sql/tb_employee-mysql.sql`）作为参考。

---

## 二、模块结构

```
b2c_mall
├── pom.xml                 # 父 POM：聚合模块 + 统一依赖版本管理
├── gateway/                # 网关模块（端口 8090）
│   └── src/main/
│       ├── java/com/lxs/b2cmall/gateway/GatewayApplication.java
│       └── resources/
│           ├── application.yml     # 路由规则 + StripPrefix
│           └── bootstrap.yml       # Nacos 注册与配置
├── shop/                   # 业务服务模块（端口 8081）
│   └── src/main/
│       ├── java/com/shop/          # 16 个包，98 个类
│       └── resources/
│           ├── application.yml     # 数据源 / Redis / MyBatis / 自定义前缀
│           └── bootstrap.yml       # Nacos 注册与配置
├── sql/                    # 建库脚本（9 个）
├── api-test.http           # 接口测试合集（73 条用例）
├── mvnw / mvnw.cmd         # Maven Wrapper，无需本机安装 Maven
└── .mvn/wrapper/           # Wrapper 配置
```

### 职责划分

| 模块 | 端口 | 职责 |
|---|---|---|
| `gateway` | 8090 | 唯一对外入口。接收 `/shop/**` 请求，剥离 `/shop` 前缀后按服务名 `lb://shop` 负载均衡转发 |
| `shop` | 8081 | 全部业务逻辑：认证、商品、购物车、订单、支付、后台管理 |

> **设计说明**：需求文档建议拆成 `user`/`shop`/`order`/`pay` 四个模块，同时注明"也可并入一个模块"。
> 本项目采用后者 —— 两个模块的架构已能完整演示服务注册、网关路由、跨服务调用（`lb://`）等微服务核心能力，
> 同时避免了多模块下共享 DTO/工具类需要额外抽 `common` 模块的复杂度。

---

## 三、快速开始

### 3.1 前置条件

| 依赖 | 要求 |
|---|---|
| JDK | 8 及以上 |
| Maven | 无需安装，用仓库自带的 `mvnw` |
| Nacos | 需可访问的服务端（本项目联调使用课程提供的实例，地址见 `bootstrap.yml`） |
| Redis | 需可访问的服务端（地址见 `shop/src/main/resources/application.yml`） |

### 3.2 重建数据库（必须，仓库未包含 shop.db）

`shop.db` 属于运行期数据，已被 `.gitignore` 排除。**clone 后必须先建库**，共 13 张表。

```bash
# 在项目根目录执行，按顺序建表（顺序有依赖：被外键引用的表要先建）
python -c "
import sqlite3
con = sqlite3.connect('shop/shop.db')
for f in ['sql/shop.sql',            # tb_shop, tb_messages
          'sql/employee.sql',        # tb_employee, tb_login_logs
          'sql/tb_product-ddl.sql',  # tb_category, tb_product
          'sql/tb_user-ddl.sql',     # tb_user（含商品种子数据）
          'sql/tb_order-ddl.sql',    # tb_order, tb_order_item, tb_order_status_log, tb_state_machine（含 8 条状态机规则）
          'sql/tb_cart-ddl.sql',     # tb_cart, tb_promotion（含满减规则）
          'sql/tb_messages-seed.sql']:
    con.executescript(open(f, encoding='utf-8').read())
    print('OK', f)
con.commit()
"
```

> **建表语句本身是幂等的**（全部使用 `CREATE TABLE IF NOT EXISTS`），但**种子数据部分不是**。
> 重复执行前请对照下表，尤其注意 `tb_order-ddl.sql` 和 `tb_user-ddl.sql`：

| 脚本 | 种子数据行为 | 重复执行的影响 |
|---|---|---|
| `shop.sql` / `employee.sql` | 仅建表，无 INSERT | 无影响 |
| `tb_product-ddl.sql` | 重建 6 条类目 | 类目被清空重建（商品数据保留） |
| `tb_user-ddl.sql` | 追加 5 条测试商品，**无去重** | ⚠️ 商品会翻倍，跑 N 次就有 5N 条 |
| `tb_order-ddl.sql` | **先删空订单三表**，再灌 2 笔测试订单 | ⚠️ 已有订单全部丢失，重置为 2 笔 |
| `tb_cart-ddl.sql` | 按名称删后重建满减规则 | 无影响 |
| `tb_messages-seed.sql` | 按标题删后重建 5 条站内信 | 无影响 |

> 结论：**全套脚本只在全新库上跑一次**。如果库里已经有自己的验收数据，不要重跑
> `tb_order-ddl.sql` 和 `tb_user-ddl.sql`。

> 另外两点注意：
> 1. `tb_shop` 表**没有种子数据** —— 店铺行是由 `/auth/register` 接口创建的。
>    所以全新库请先按 3.5 注册一个管理员，之后 `tb_messages-seed.sql` 里的
>    `(SELECT id FROM tb_shop LIMIT 1)` 才能取到店铺 id。
> 2. `tb_product` 的种子数据在 `tb_user-ddl.sql` 里（5 条测试商品），
>    所以那个脚本除了建 `tb_user` 表，还负责让前台商品列表有东西可看。

### 3.3 修改数据源路径（必须）

`shop/src/main/resources/application.yml` 中的数据源是**绝对路径**：

```yaml
spring:
  datasource:
    url: jdbc:sqlite:<你的项目路径>/shop/shop.db?foreign_keys=on
```

⚠️ **不改这一行会静默新建一个空库**，表现为"表全部不见了"但不报错。请改成你自己的实际路径，且路径中的空格不要 URL 编码。

### 3.4 启动

必须先启动 Nacos，再按顺序启动服务：

```bash
# 1. 启动网关（可在 IDEA 直接运行 GatewayApplication）
./mvnw -pl gateway spring-boot:run

# 2. 启动业务服务（另开一个终端）
./mvnw -pl shop spring-boot:run
```

Windows 用户也可用 `mvnw.cmd`，或在 IDEA 中分别运行两个 `*Application` 类。

### 3.5 验证

```bash
# 1) 确认两个服务已注册到 Nacos（namespace: zhanghao）
#    期望看到 gateway 和 shop
GET http://<nacos地址>:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50&namespaceId=zhanghao

# 2) 直连 shop（未经网关，期望 401 —— 未授权拦截生效）
GET http://localhost:8081/test/hello

# 3) 走网关（验证路由 + StripPrefix=1，期望 401）
GET http://localhost:8090/shop/test/hello

# 4) 走网关注册管理员（首次使用必须先注册，会同时创建店铺）
POST http://localhost:8090/shop/auth/register
Content-Type: application/json

{"shopName":"测试店铺","username":"admin","password":"123456"}

# 5) 走网关登录，拿到令牌
POST http://localhost:8090/shop/auth/login
Content-Type: application/json

{"username":"admin","password":"123456"}
```

拿到令牌后，后续请求在请求头携带（支持两种写法）：

```
Authorization: Bearer <token>
# 或
token: <token>
```

> 完整用例见根目录 `api-test.http`：共 **79 条可直接发送的请求**，其中 73 条为业务流用例
> （按 A~Z / AA~AZ / BA~BR 分组，覆盖注册 → 登录 → 上架 → 下单 → 支付 → 发货 → 收货全流程），
> 另有 6 条用于验证网关链路与 Nacos 连通性。用 IDEA 打开后逐条点击绿色箭头即可发送。

---

## 四、接口清单

共 **32 个业务接口**（后台 16 + 前台 16），另有 1 个用于验证网关联通的测试接口。
以下均为**剥掉 `/shop` 前缀后**服务内的真实路径；经网关访问时需加 `/shop` 前缀。

### 4.1 后台管理接口（16）

| 控制器 | 方法 | 路径 | 说明 |
|---|---|---|---|
| AuthController | POST | `/auth/login` | 管理员登录 |
| | POST | `/auth/register` | 注册店铺 + 管理员账号 |
| | POST | `/auth/logout` | 退出登录（销毁令牌） |
| DashboardController | GET | `/dashboard/messages` | 站内信列表 |
| | GET | `/dashboard/messages/unread` | 未读消息数 |
| | POST | `/dashboard/messages/{id}/read` | 标记已读 |
| | GET | `/dashboard/order-summary` | 订单统计概览 |
| ProductController | POST | `/product/publish` | 商品上架（模板方法） |
| | GET | `/product/list` | 商品列表 |
| | POST | `/product/{id}/status` | 上架/下架 |
| OrderController | GET | `/order/list` | 订单列表 |
| | GET | `/order/{id}` | 订单详情 |
| | POST | `/order/ship` | 发货（状态机） |
| | POST | `/order/confirm` | 确认收货 |
| | POST | `/order/cancel` | 取消订单 |
| | GET | `/order/rules` | 查询状态机规则表 |

### 4.2 前台买家接口（16）

| 控制器 | 方法 | 路径 | 说明 |
|---|---|---|---|
| MallAuthController | POST | `/mall/auth/register` | 买家注册 |
| | POST | `/mall/auth/login` | 买家登录 |
| MallProductController | GET | `/mall/product/list` | 商品列表（免登录） |
| | GET | `/mall/product/{id}` | 商品详情（免登录） |
| MallCategoryController | GET | `/mall/category/list` | 分类导航（免登录） |
| MallCartController | POST | `/mall/cart/add` | 加入购物车 |
| | POST | `/mall/cart/update` | 修改数量 |
| | DELETE | `/mall/cart/{cartId}` | 删除购物车项 |
| | GET | `/mall/cart/list` | 购物车列表 |
| | GET | `/mall/cart/preview` | 结算预览（算满减） |
| MallOrderController | POST | `/mall/order/submit` | 提交订单 |
| | POST | `/mall/order/pay` | 支付（策略模式） |
| | GET | `/mall/order/pay-types` | 支持的支付方式 |
| | GET | `/mall/order/list` | 我的订单 |
| | GET | `/mall/order/{id}` | 订单详情 |
| | GET | `/mall/order/{id}/state` | 查询当前状态对象（状态模式） |
| TestController | GET | `/test/hello` | 仅用于验证网关链路 |

### 4.3 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

未登录或令牌过期统一返回 **HTTP 401** + `{"code":401,"message":"未登录或登录已过期"}`。

---

## 五、数据库设计

共 **13 张表**（SQLite）。

| 表名 | 说明 | 建表脚本 |
|---|---|---|
| `tb_shop` | 店铺 | `shop.sql` |
| `tb_messages` | 站内信 | `shop.sql` / `tb_messages-seed.sql` |
| `tb_employee` | 后台管理员 | `employee.sql` |
| `tb_login_logs` | 登录日志 | `employee.sql` |
| `tb_user` | 前台买家 | `tb_user-ddl.sql` |
| `tb_category` | 商品分类 | `tb_product-ddl.sql` |
| `tb_product` | 商品 | `tb_product-ddl.sql` |
| `tb_order` | 订单 | `tb_order-ddl.sql` |
| `tb_order_item` | 订单明细（含下单快照） | `tb_order-ddl.sql` |
| `tb_order_status_log` | 订单状态变更留痕 | `tb_order-ddl.sql` |
| `tb_state_machine` | **状态机规则表** | `tb_order-ddl.sql` |
| `tb_cart` | 购物车 | `tb_cart-ddl.sql` |
| `tb_promotion` | 促销/满减规则 | `tb_cart-ddl.sql` |

### 订单状态与状态机规则

订单状态共 6 个：`PENDING_PAY` 待付款 / `PENDING_SHIP` 待发货 / `PENDING_RECEIVE` 待收货 /
`SUCCESS` 交易成功 / `FAILED` 交易失败 / `REFUNDING` 待退款。

状态流转**不写死在 if-else 里**，而是配置在 `tb_state_machine` 表的 8 条规则中：

| from_status | event | to_status |
|---|---|---|
| PENDING_PAY | PAY_SUCCESS | PENDING_SHIP |
| PENDING_PAY | CANCEL_TIMEOUT | FAILED |
| PENDING_SHIP | SHIP | PENDING_RECEIVE |
| PENDING_RECEIVE | CONFIRM | SUCCESS |
| PENDING_PAY / PENDING_SHIP / PENDING_RECEIVE / SUCCESS | APPLY_REFUND | REFUNDING |

> 新增一条流转规则只需 `INSERT` 一行，不用改代码、不用重新编译。

---

## 六、五种设计模式落点

| # | 模式 | 落点包 | 解决的问题 |
|---|---|---|---|
| 1 | **观察者模式** | `event/` + `observer/` | 登录/注册成功后要发日志、发欢迎站内信、初始化账号、刷新用户状态。用事件解耦，主流程只管发事件，副作用各自订阅 |
| 2 | **模板方法** | `template/` | 商品上架流程固定（校验 → 落库 → 发通知），但实物商品要校验库存、虚拟商品要生成兑换码。父类定骨架，子类填差异 |
| 3 | **状态机** | `statemachine/` | 订单流转规则配置在数据库，运行期查表驱动，避免状态判断散落在各处 |
| 4 | **策略模式** | `pay/` | 支付宝/微信/PayPal/其他 四种支付方式算法可替换，调用方按 `payType` 选择策略 |
| 5 | **状态模式** | `state/` | 订单在不同状态下"能做什么"不同。每个状态一个类，行为随状态改变而改变 |

### 模式 3 与模式 5 的关系（本项目的关键设计）

这两个模式容易混淆，本项目的处理方式是**让它们协作而非重复**：

- **状态机（模式 3）** 回答"**能不能**从 A 状态走到 B 状态" —— 规则存在 `tb_state_machine`，是**数据驱动**的。
- **状态模式（模式 5）** 回答"当前状态下**该做什么**" —— 每个状态类封装自己的行为，是**对象驱动**的。

`OrderContext.moveTo()` **委托** `OrderStateMachine.fire()` 完成合法性校验，
因此全项目只有**一份**规则表（`tb_state_machine` 的 8 条），状态模式没有重复定义任何一条规则。

### 观察者模式的一个边界说明

观察者使用 Spring 的 `ApplicationEventPublisher` / `@EventListener`，**默认是同步调用**：
主流程发布事件后会等待所有观察者执行完毕才返回。这意味着：

- 观察者**不应该**抛出异常，否则会影响主业务流程；
- 观察者是**单向接收**，不能向发布者返回结果（所以登录时若需要观察者的产出，必须写在 Service 里而不是观察者里）。

---

## 七、包结构说明（shop 模块 16 个包）

| 包 | 文件数 | 职责 |
|---|---|---|
| `dto` | 15 | 请求/响应数据传输对象 |
| `mapper` | 13 | MyBatis 数据访问接口 |
| `entity` | 13 | 数据库表实体 |
| `service` | 10 | 业务逻辑（决策者） |
| `controller` | 9 | 接口层 |
| `state` | 7 | 状态模式（模式 5） |
| `pay` | 6 | 策略模式（模式 4） |
| `account` | 5 | 账号存储抽象（后台管理员 / 前台买家） |
| `observer` | 4 | 观察者实现（模式 1） |
| `common` | 4 | 统一响应、异常、异常处理器、用户上下文 |
| `template` | 3 | 模板方法（模式 2） |
| `statemachine` | 3 | 状态机（模式 3） |
| `event` | 2 | 事件对象（模式 1 的发布侧） |
| `web` | 1 | 测试控制器 |
| `interceptor` | 1 | 令牌校验拦截器 |
| `config` | 1 | 拦截器注册与白名单 |

---

## 八、认证机制

采用 **Redis 不透明令牌**（而非 JWT），核心原因是**支持主动失效** —— 退出登录可以立刻让令牌作废，JWT 做不到。

| 项 | 值 |
|---|---|
| 令牌格式 | UUID 去横线（32 位随机串） |
| 存储 | Redis String，value 为用户信息 JSON |
| 有效期 | 30 分钟 |
| 续期策略 | 滑动续期：剩余不足 10 分钟且仍在使用，自动续 30 分钟 |
| Key 前缀 | `zhanghao:shop:token:`（多人共用同一 Redis 时用于隔离） |
| 传递方式 | `Authorization: Bearer <token>` 或 `token: <token>` |

拦截器白名单（`config/WebMvcConfig.java`）放行了登录、注册、商品浏览、分类导航以及 `/error`。
其中**必须放行 `/error`** —— Spring Boot 出错时会转发到 `/error`，而转发会再次经过拦截器，
不放行会导致真实异常被二次拦截成 401，日志里只看得到"未授权"。

---

## 九、遇到的坑与排查提示

这些是本项目实际踩过的坑，记录在此以便复现问题时快速定位：

| 现象 | 根因 | 处理 |
|---|---|---|
| 表"全部不见了" | 数据源用了绝对路径，换目录后 SQLite **静默新建空库** | 修改 `application.yml` 的 `url` |
| 后端 503 | 网关找不到服务实例（shop 没启动或未注册上 Nacos） | 查 Nacos 服务列表 |
| 后端 404 | 服务正常但路径无 Controller 匹配 | 检查是否漏了 `/shop` 前缀或写错路径 |
| 接口莫名 401 | 请求经网关进来，白名单要写**剥掉前缀后**的路径 | 白名单写 `/auth/login` 而非 `/shop/auth/login` |
| 500 变成 401 | 出错转发到 `/error` 被拦截器二次拦截 | 白名单放行 `/error` |
| 启动报连不上 Nacos | `bootstrap.yml` 未生效 | 确认引入了 `spring-cloud-starter-bootstrap` 依赖 |
| 网关启动失败 | 误引入 `spring-boot-starter-web`（Gateway 基于 WebFlux，与 Servlet 栈冲突） | 网关模块不要引 web starter |
| YAML 报 `mapping values are not allowed here` | 值里含冒号未加引号（如 `key-prefix: zhanghao:shop:token:`） | 整串用双引号包起来 |

---

## 十、已知限制与后续计划

- **消息队列暂未接入**：需求文档建议使用 RocketMQ 做异步解耦，当前观察者模式基于 Spring 内置事件实现（同步）。
  这满足需求中"简单版"的要求，但尚未接入真实 MQ 中间件，后续计划补齐 RocketMQ 版本。
- **无前端页面**：项目定位为后端服务，12 张原型图对应的交互目前均以 JSON 接口形式提供。
- **单库部署**：当前所有业务表集中在 `shop` 模块的一个 SQLite 库中，未做分库拆分。
- **`tb_employee` 未设角色字段**：后台目前只有单一管理员角色，未做 RBAC 权限细分。

---

## 十一、开发笔记

项目开发过程沉淀了完整的设计与技术文档（未包含在本仓库中），包括：

- 需求拆解与实施步骤
- 各阶段操作步骤与避坑清单
- 设计模式落点对照表
- 项目知识点总复习（六个阶段）
- 项目架构说明（含答辩问答准备）

---

## 许可

本项目为课程学习项目，仅供学习与交流使用。
