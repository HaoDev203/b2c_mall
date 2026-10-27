# W 系列 · Web 接口契约验收（19 个接口 / 33 个用例）

> 与 `PROGRESS-需求验收.md` 的 88 条**分开记**，不要合并成一个数：
> **88 条验「Service 逻辑对不对」，W 系列验「外面能不能进来」。**
> 两层测的东西没有交集 —— 155 条 Service 测试一条都碰不到 HTTP 层。
>
> 测试类：`mall-tdd/src/test/java/com/mall/tdd/controller/WebLayerContractTest.java`
> 技术：`@WebMvcTest`（只起 Controller 那一圈 Bean，不连库、不起 MyBatis）
> 完成时间：2026-10-27

---

## 一、为什么会有这一批

`mall-tdd` 的 88 条验收全部落在 Service 层，`src/main` 里 **0 个 `@RestController`**。
`spring-boot:run` 能正常启动（Tomcat 8082，2.3 秒），但 19 个业务路径**全部 404**。

这不是 TDD 的要求 —— TDD 只要求「先写会失败的测试」，Controller 的测试就是
`@WebMvcTest` 那一种。真实原因是测试选型留下的盲区：

1. 为了跑得快，Service 层定的是「纯 Mockito，不启动 Spring」；
2. `MockMvc` 必须启 Spring 上下文，于是「TDD 能驱动的范围」天然止于 Service；
3. 88 条是按 **Service 方法**拆的，不是按 **HTTP 接口**拆的，Controller 永远拿不到失败的测试；
4. 于是「没有验收条目 → 无人守护 → 先不写」被当成了正当理由 —— 这一步是判断错误。

**教训（可迁移）**：测试能力到哪，代码就长到哪；超出测试能力的层会自然萎缩。
真实项目里不会出现这种情况，因为有三道闸门：前后端联调、接口文档、code review / CI。
本项目这三道闸门一个都没有，唯一指标是「88 条勾选」，而它只衡量**需求覆盖**、
不衡量**可交付性**——单一指标 + 无复核 = 缺口必然漏。

---

## 二、19 个接口

| # | 方法 | 路径 | Controller | 用例 |
|---|---|---|---|---|
| 1 | GET | `/api/mall/category/list` | MallProductController | W01 |
| 2 | GET | `/api/mall/product/list` | MallProductController | W01 / W03 |
| 3 | GET | `/api/mall/product/{id}` | MallProductController | W01 / W03 |
| 4 | GET | `/api/dashboard/cards` | DashboardController | W01 / W08 / W09 |
| 5 | GET | `/api/dashboard/metrics` | DashboardController | W01 |
| 6 | GET | `/api/dashboard/trend` | DashboardController | W01 |
| 7 | GET | `/api/dashboard/messages` | DashboardController | W01 |
| 8 | GET | `/api/mall/order/list` | MallOrderController | W01 / W02 / W03 / W03b |
| 9 | DELETE | `/api/mall/order/{id}` | MallOrderController | W01 |
| 10 | POST | `/api/mall/order/buy-now` | MallOrderController | W01 |
| 11 | GET | `/api/shop/product/list` | ShopProductController | W01 / W12 |
| 12 | DELETE | `/api/shop/product/{id}` | ShopProductController | W01 / W12 |
| 13 | POST | `/api/shop/product/batch` | ShopProductController | W01 / W11 |
| 14 | GET | `/api/shop/product/export` | ShopProductController | W01 / W05 / W06 |
| 15 | POST | `/api/shop/product/import` | ShopProductController | W01 / W07 / W07b |
| 16 | GET | `/api/shop/order/list` | ShopOrderController | W01 |
| 17 | POST | `/api/shop/order/ship` | ShopOrderController | W01 |
| 18 | POST | `/api/shop/order/batch-ship` | ShopOrderController | W01 / W11 |
| 19 | GET | `/api/shop/order/export` | ShopOrderController | W01 / W06b |

> 契约表原列 20 个，`GET /api/mall/order/{id}` 已剔除：需求原文摘要 7 项里没有「订单详情」，
> B3 的 9 条验收字段断言也全在列表卡片上，是拆解时多列的接口。见拆解-00 §三 的更正说明。

---

## 三、12 组用例

| 编号 | 断言的是什么 | 为什么必须测 |
|---|---|---|
| **W01** | 19 个路径全部命中（只断言非 404/405） | 路径拼错、`@RequestMapping` 少写一段、方法注解写错，**编译器全不管**。没有这条，其余 11 组可以全绿而前端一个接口都调不到 |
| **W02** | Service 返回未登录时，`Result.code` 必须是 401 | Controller 顺手写 `Result.ok(...)` 会把 401 变成「成功但空」，前端不跳登录页，用户看到「你没有订单」而不是「登录过期」 |
| **W03** | 路径变量与查询参数正确绑定到 Service | `@PathVariable` 少写 value、`{id}` 配 `@RequestParam` —— 编译期正常，运行时 500 或拿到 null |
| **W03b** | 缺省分页参数传 `null`，不在 Controller 兜底 | 分页默认值和上限（C 端 50 / B 端 100）已在 Service 归一化，两处各写一遍迟早不一致 |
| **W04** | 反射：Controller 签名不许出现 `userId` / `shopId` 参数 | 只断言「这次没传」防不住以后有人加个 `?shopId=`，那是最典型的水平越权。同 U05「OrderMapper 不许有 delete 方法」的思路 |
| **W05** | 导出带 `Content-Disposition: attachment` + xlsx 的 Content-Type，字节原样写出 | 直接返回 `Result<byte[]>` 会被序列化成 base64 JSON，浏览器不弹下载、Excel 打不开 |
| **W06** | **导出失败时返回 JSON 错误，不能写出 0 字节 .xlsx** | Service 返回 `Result.fail` 时 data 是 null，写出去就是 0KB 坏文件，Excel 报「文件已损坏」，**而日志里一条错误都没有**（没抛异常），是最难查的那类问题 |
| **W06b** | 订单导出同样带下载头 | 两个导出接口各写一套映射，容易只修好一个 |
| **W07** | multipart 文件能到达 Service | 注解写 `@RequestParam("excel")` 而前端传 `file` → 直接 400，Service 一次都不会被调用 |
| **W07b** | 没传/空文件给可读提示，不是 NPE 500 | 商家只是忘了选文件，不该看到「系统错误」 |
| **W08** | 请求头身份写进 `UserContext` | Service 里 20 多处 `UserContext.getUserId()`，一个填不进去，整个模块对外永久「未登录」 |
| **W09** | **请求结束后 `UserContext` 必须清空** | Tomcat 线程复用，漏了 clear 会把上一个请求的身份带给下一个 —— 表现是**全站串号**，手工点页面 100% 发现不了，只在并发或特定调度下显现 |
| **W10** | 未捕获异常仍返回 `Result`，且不泄露 SQL 原文 | 没有全局处理会返回 Spring 默认错误 JSON，前端读 `data.code` 得到 `undefined`，所有失败都变成「未知错误」 |
| **W11** | 批量设置 / 批量发货的 JSON 数组绑定 | `@RequestBody` 漏了 → NPE；DTO 字段名和前端不一致 → 绑成空集合，返回「成功 0 条」，用户以为生效了 |
| **W12** | 未知路径 404、方法不匹配 405，**不许被兜底吞成 200** | 有人为「统一返回格式」加 `/**` 兜底后，拼错的路径会返回 200 空数据，W01 从此永远绿 |

---

## 四、变异验证（8 处 / 两轮，10 条精确红）

| 改坏什么 | 红的用例 | 红在哪 |
|---|---|---|
| 分类路径 `/category/list` → `/categories/list` | W01[1] | 「未命中，返回 404 —— 路径拼错」 |
| `@RequestParam("page")` → `"pageNo"` | W03 | `expected: <2> but was: <null>` |
| 拦截器注释掉 `UserContext.clear()` | W09 | 「上一个请求的 shopId 泄漏到了下一个请求」`expected null but was 1` |
| `if (!success)` → `if (!success && false)` | W06 | 「失败时不能带下载头」实际返回了 `filename="products_20261027.xlsx"` |
| 拦截器不写 `UserContext.set(...)` | W08 / W09 | 「userId 没进 UserContext」`expected 1001 but was null` |
| 导出丢 `Content-Disposition` | W05 / W06b | 「缺 Content-Disposition，浏览器不会下载」 |
| `ResponseEntity.status(status)` → `.ok(...)` | W12 | 「方法不支持时必须是 405」`expected 405 but was 200` |
| Controller 加一个 `shopId` 参数 | W04 | 「不许从请求传：ShopProductController#importExcel(shopId)」 |

**分两轮而不是一次全改**：上一批（P22/P23）的教训是单处变异会把测试卡在更早的断言上、
掩盖后面几处。一次只改一组，红的才可信。

**W04 那条特别值得记**：它证明了 `maven-compiler-plugin` 的 `<parameters>true</parameters>` 生效。
不开这个选项，反射读到的是 `arg0` / `arg1`，这条断言会**永远假绿**。

---

## 五、真 HTTP 端到端核对（16 项，`spring-boot:run` + curl）

| 验的是 | 结果 |
|---|---|
| 未登录看 C 端订单 | `{"code":401,"msg":"请先登录"}` |
| 带 `X-User-Id: 1001` 看订单 | 真实订单 157 / 150，店铺名、金额、状态齐全 |
| 分类导航（无登录） | 服饰鞋包 / 家用电器 / 手机数码 …… |
| Dashboard 卡片 | `pendingPay 11 / pendingShip 4 / pendingReceive 3 / completed 18 / refundAfterSale 2 / stockWarning 3` |
| 未知路径 | HTTP 404 |
| 商品导出 | 4290 字节，`PK` 文件头，`attachment; filename="products_20261027.xlsx"` |
| 订单导出 | 7173 字节，`PK` 文件头 |
| 后台商品列表 `size=2` | `total: 7`，真实商品字段 |
| 本店发货 148 | `code 200` → 库中变 `PENDING_RECEIVE` + `SF1001` |
| **越权发店 2 的 189** | `code 500`「订单不存在或无权操作」→ **189 状态未变** |
| 批量发货 `[152,189,166]` | `total 3 / success 2 / failed 1`，明细逐条列出，189 被拒、另两条成功 |
| 导入 round-trip（导出文件再导回） | `total 7 / insert 0 / update 7 / failed 0`，商品数仍 **7**（没翻倍） |
| 导入空文件 | `{"code":500,"msg":"请选择要导入的 Excel 文件"}` |
| GET 打只接受 DELETE 的路径 | HTTP 405 |

> **最强的一条是导入 round-trip**：`insert 0 / update 7`，商品数 7 → 7。
> 这证明 P23 的 upsert 判定（按 `product_code`）和「导出带商品编码列」那个决定是对的 ——
> 少了编码列，这 7 行会全部变成新增，一店商品直接翻倍。
>
> **第二强的是越权发货**：同一批三条里一条被拒，另两条照常成功，被拒那条数据一个字节都没动。
> 这说明店铺隔离写在 SQL 的 `WHERE` 里，不是「先查再判」。

---

## 六、两个写在代码里的临时决定（必须知道）

### 1. 身份来自请求头，这不是登录

`UserContextInterceptor` 从 `X-User-Id` / `X-Shop-Id` 读身份写进 ThreadLocal。
**任何人手工加一个头就能冒充任意用户** —— 这是 `mall-auth`（87 项，仍是 0/87）
没有 token 体系时的临时透传，不是鉴权。

接入登录后改动只在这一处：换成解析 `Authorization: Bearer <token>`，其余代码不用动。
这正是当初把身份收敛到 `UserContext` 的目的。

### 2. HTTP 状态码只在异常时才用

正常业务失败（含未登录）一律 **HTTP 200 + `body.code`**（200 / 500 / 401），
只有标准异常（400 / 404 / 405 / 415）和未捕获异常（500）才用 HTTP 状态码。

理由：88 条 Service 验收全部按 `Result.code` 断言，Controller 只透传，
不做 code → status 的二次映射，避免两套判断逻辑。**前端请读 `body.code` 判断登录态。**

---

## 七、汇总

| 项 | 数 |
|---|---|
| 接口 | 19 |
| 测试用例（含参数化展开） | 33 |
| 断言分组 | 12 |
| 变异验证 | 8 处 → 10 条精确红 |
| 端到端核对 | 16 项 |
| 累计测试 | **188 条全绿**（155 需求验收 + 33 Web 契约） |

```
mvn -pl mall-tdd test
-> Tests run: 188, Failures: 0, Errors: 0, Skipped: 0
```
