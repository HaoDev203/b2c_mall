# B2 · 店铺 Dashboard — 23 条

> 需求原文位置：`项目需求v.1.1.docx` → TO B → **店铺Dashboard**
>
> 服务：`DashboardService`
> 测试类：`DashboardServiceTest`
> 接口：`GET /api/dashboard/cards` / `metrics` / `trend` / `messages`

---

## 需求原文摘要

| 原文 | 拆出几条 |
|---|---|
| 「只查看当前登录店铺数据，不可查看其他店铺」 | D01 / D09 / D23 |
| 「6 个统计卡片：待付款 0、待发货 12、待收货 2、已完成 24、退款/售后 1、库存预警 3」 | D02~D08 |
| 「折线图 X 轴 24 小时，蓝色今日支付金额、绿色昨日支付金额」 | D10~D13 |
| 「4 项核心经营数据：支付客户数、支付订单数、退款金额、客单价，每项带同比昨日变化率」 | D14~D19 |
| 「每条消息携带订单编号；按时间倒序；三种消息类型」 | D20~D22 |
| 「数字 + 业务名称 + **图标**」「折线图**颜色**」「鼠标悬浮提示」 | **不做**（前端，见总览 §五） |

---

# 功能点 1 · 顶部订单状态卡片（D01~D09，9 条）

## D01 · 未取到 shopId 直接失败，不查库

- **测试方法**：`should_fail_without_querying_when_shop_id_is_absent`
- **对应需求**：权限段
- **前置**：`UserContext.getShopId()` 返回 `null`
- **断言**：
  - 返回 `code != 200`
  - `verify(orderMapper, never()).countByStatus(any(), any())` —— **一个查询都不许发**
- **红在哪**：`DashboardService` 不存在 → 编译红
- **陷阱**：**`UserContext` 是静态 ThreadLocal，不要 `mockStatic`**。用 `@BeforeEach` 手动 `UserContext.set(...)`、`@AfterEach` `UserContext.clear()`。`mockStatic` 和参数化测试一起用会互相污染
- **为什么重要**：shopId 为 null 时如果继续查库，SQL 会变成 `WHERE shop_id = NULL` → 查不到任何数据 → 页面全 0。商家会以为「今天一单没有」，实际是登录态丢了。**失败要显式失败，不能静默返回 0**

---

## D02 · 返回体含 6 个卡片 key

- **测试方法**：`should_return_six_status_card_keys`
- **对应需求**：卡片模块「6 个统计卡片」
- **断言**：返回 Map 的 key 集合**恰好等于**
  ```
  pendingPay, pendingShip, pendingReceive, completed, refundAfterSale, stockWarning
  ```
- **红在哪**：无实现
- **陷阱**：用 `containsExactlyInAnyOrder(...)` 断言**恰好这 6 个**，不要用 6 个 `containsKey`。后者在实现多返回一个 `tradeFailed` 时**照样绿** —— 一期末 shop 就多了一张「交易失败」卡，需求里没有
- **建议**：写成 `@ParameterizedTest`，6 个 key 各跑一次 + 1 次总数断言，能一并覆盖 D03~D08 的取值正确性

---

## D03 · 待付款数 = 本店 PENDING_PAY 订单数

- **测试方法**：`should_count_pending_pay_orders`
- **对应需求**：「待付款订单：统计当前状态为待付款订单总数」
- **前置**：`orderMapper.countByStatus(SHOP_ID, "PENDING_PAY")` → `5L`
- **断言**：`data.get("pendingPay")` 等于 `5`
- **红在哪**：无实现
- **陷阱**：桩值要 `5L`（long），不能 `5`（int）—— 返回类型是 `long` 时会编译不过或匹配不上

---

## D04 · 待发货数 = 本店 PENDING_SHIP 订单数

- **测试方法**：`should_count_pending_ship_orders`
- **对应需求**：「已付款、尚未发货订单总数」
- **前置**：桩 `12L`（**用需求原文给的示例数字**）
- **断言**：`data.get("pendingShip")` 等于 `12`
- **红在哪**：无实现
- **建议**：用需求原文的 6 个示例数（0/12/2/24/1/3）当桩值，这样断言和文档能一一对照，答辩时直接指着说「这里对应需求第几行」

---

## D05 · 待收货数 = 本店 PENDING_RECEIVE 订单数

- **测试方法**：`should_count_pending_receive_orders`
- **对应需求**：「已发货、买家未确认收货订单总数」
- **前置**：桩 `2L`
- **断言**：`data.get("pendingReceive")` 等于 `2`
- **红在哪**：无实现

---

## D06 · 已完成数 = 本店 SUCCESS 订单数

- **测试方法**：`should_count_completed_orders`
- **对应需求**：「交易完结（买家确认收货）订单总数」
- **前置**：桩 `24L`
- **断言**：`data.get("completed")` 等于 `24`
- **红在哪**：无实现
- **陷阱**：「已完成」对应 `SUCCESS` 还是 `COMPLETED`？**必须和状态机枚举对齐**。这条测试的价值就是钉死这个映射

---

## D07 · 退款/售后数

- **测试方法**：`should_count_refund_and_after_sale_orders`
- **对应需求**：「正在进行退款、售后申请单据总数」
- **前置**：桩 `1L`
- **断言**：`data.get("refundAfterSale")` 等于 `1`
- **红在哪**：无实现
- **陷阱**：需求说的是「退款 **和** 售后」，**是两类单据还是一个状态**？一期末 shop 只有一个 `REFUNDING` 状态。建议**先按单一状态实现**，并在测试注释里注明「需求未区分退款单与售后单，本期合并计算」—— 这是需求缺口，答辩要能说清

---

## D08 · 库存预警数 = 库存 ≤ 阈值的商品数

- **测试方法**：`should_count_stock_warning_products`
- **对应需求**：「库存低于阈值的商品数量」
- **前置**：`productMapper.countStockWarning(SHOP_ID)` → `3L`
- **断言**：`data.get("stockWarning")` 等于 `3`
- **红在哪**：`ProductMapper.countStockWarning` 不存在 → 编译红
- **陷阱（关键）**：SQL 必须是 `stock <= stock_warn`。**`stock_warn IS NULL` 的商品有意不预警** —— 不要为了「看起来完整」加 `OR stock_warn IS NULL`，那会让所有没配预警线的商品一上架就被标红
- **另一处**：需求写「**低于**阈值」，代码是 `<=`（含等于）。**以需求原文为准应写 `<`**？不 —— 库存 3、阈值 3 时按 `<=` 预警更符合直觉，`<` 会漏掉刚好等于的那批。**这条要在测试注释明确写「采用 <=，含等于」**，并说明理由

---

## D09 · 数据隔离：其他店铺数据不混入

- **测试方法**：`should_not_mix_other_shop_data`
- **对应需求**：权限段「只查看当前登录店铺数据，不可查看其他店铺」
- **前置**：`SHOP_ID = 1`，另一个店铺 `SHOP_ID_OTHER = 2` 有 99 单
- **断言**：
  - **每一次** Mapper 调用的第一个参数都是 `eq(1L)`，`never()` 出现 `2L`
  - 6 个卡片数字都等于 shop 1 的桩值，不受 shop 2 影响
- **红在哪**：无实现
- **陷阱**：这是**最容易漏测的一条**。手测永远只登自己的账号，跨店数据混入根本发现不了。断言要覆盖**所有** Mapper 调用（用 `ArgumentCaptor` 或逐条 `verify`），不能只查一个
- **写法建议**：`verify(orderMapper, never()).countByStatus(eq(SHOP_ID_OTHER), any())` 逐条列出，比 `verifyNoMoreInteractions` 更精确

---

# 功能点 2 · 实时数据趋势图（D10~D13，4 条）

## D10 · 返回 24 个数据点

- **测试方法**：`should_return_24_points_for_trend`
- **对应需求**：「X 轴：一天 24 小时（0:00~23:00）」
- **断言**：
  - 今日曲线数组 size = **24**
  - 昨日曲线数组 size = **24**
- **红在哪**：`trend()` 方法不存在 → 编译红
- **陷阱**：**不能返回「有数据的小时才返回」**。前端画 24 个刻度，数据只有 5 个点会错位。必须补齐 24 个

---

## D11 · 今日曲线 = 该小时支付金额合计

- **测试方法**：`should_sum_today_paid_amount_by_hour`
- **对应需求**：「蓝色：今日支付金额」
- **前置**：`orderMapper.sumPaidAmountByHour(SHOP_ID, today)` 返回
  ```
  [(0时, 0), (9时, 128.00), (10时, 356.50), (14时, 99.00)]
  ```
- **断言**：`today[9] == 128.00`、`today[10] == 356.50`、`today[14] == 99.00`
- **红在哪**：Mapper 聚合方法不存在 → 编译红
- **陷阱**：**同一小时多笔订单要合计，不是取最后一笔**。桩数据里故意让 10 时有两笔（100 + 256.50）合成 356.50，如果实现写成覆盖而不是累加，这条会红
- **口径**：金额按 `pay_time` 的小时归属，且只算 `pay_time IS NOT NULL`（见总览 §四）

---

## D12 · 昨日曲线

- **测试方法**：`should_sum_yesterday_paid_amount_by_hour`
- **对应需求**：「绿色：昨日支付金额」
- **前置**：昨日数据 `[(11时, 200.00), (20时, 480.00)]`
- **断言**：`yesterday[11] == 200.00`、`yesterday[20] == 480.00`，其余为 0
- **红在哪**：同 D11
- **陷阱**：今日与昨日**必须是两次独立查询**，不能查一次再按日期分组。测试要 `verify` 两次调用，参数分别是 today / yesterday。否则实现可能「查最近两天再在内存里拆」，数据量一大就崩

---

## D13 · 无成交的小时返回 0，不是缺项

- **测试方法**：`should_fill_zero_for_hours_without_orders`
- **对应需求**：隐含（24 点必须齐全）
- **前置**：只有 9 时有数据
- **断言**：
  - `today[0] == 0`、`today[23] == 0`
  - 数组里**没有 `null`**
  - `today.length == 24`
- **红在哪**：实现返回稀疏数组 / Map → 断言红
- **陷阱**：`null` 会让 ECharts 断线（曲线在缺失点断开），`0` 才是正确的「无成交」。这条专门钉死「补 0 而不是补 null」

---

# 功能点 3 · 四项核心经营指标（D14~D19，6 条）

每条返回结构：`{ value: double, dod: double }`（数值 + 同比变化率）。

## D14 · 支付客户数：同一客户当日 2 单计 1 ★

- **测试方法**：`should_count_distinct_paid_customers`
- **对应需求**：「今日完成付款独立客户数量」
- **前置**：今日 3 笔已支付订单，来自 2 个 user_id（1001 两单、1002 一单）
- **断言**：`metrics.paidCustomers.value == 2`（不是 3）
- **红在哪**：Mapper 缺 `COUNT(DISTINCT user_id)` → 编译红
- **陷阱**：**这是唯一一处必须 `DISTINCT` 的指标**。桩数据必须让「订单数 ≠ 客户数」，否则写成 `COUNT(*)` 也能绿 —— 测试就白写了
- **为什么 ★**：需求原文专门写了「**独立**客户」，这是最容易实现错的一个词

---

## D15 · 支付订单数

- **测试方法**：`should_count_paid_orders`
- **对应需求**：「今日成功支付订单总量」
- **前置**：`countPaidOrders(SHOP_ID, today)` → `5`
- **断言**：`metrics.paidOrders.value == 5`
- **红在哪**：Mapper 方法不存在
- **陷阱**：口径是 `pay_time IS NOT NULL`，**不是 `status = 'PAID'`**。已支付订单会继续流转到待发货/待收货/成功，按 status 算会漏掉大部分

---

## D16 · 退款金额

- **测试方法**：`should_sum_refund_amount`
- **对应需求**：「今日退款总金额」
- **前置**：`sumRefundAmount(SHOP_ID, today)` → `88.50`
- **断言**：`metrics.refundAmount.value == 88.50`
- **红在哪**：Mapper 方法不存在
- **陷阱**：无退款时返回 `0.00`，**不能返回 `null`**（`COALESCE(SUM(...), 0)`）。`null` 会让前端格式化崩

---

## D17 · 客单价 = 总支付金额 ÷ 支付订单数

- **测试方法**：`should_compute_average_order_value`
- **对应需求**：「今日总支付金额 ÷ 支付订单数」
- **前置**：总支付金额 `1000.00`，支付订单数 `4`
- **断言**：`metrics.averageOrderValue.value == 250.00`
- **红在哪**：无实现
- **陷阱（关键）**：**分母为 0 时必须返回 `0.00`，不能抛 `ArithmeticException` 也不能返回 `NaN`**。另写一条：`should_return_zero_average_when_no_paid_order`
- **精度**：用 `double` 会有 `0.1 + 0.2 = 0.30000000000000004` 问题。断言用 `isCloseTo(250.00, within(0.01))`，不要 `isEqualTo(250.00)`

---

## D18 · 同比公式

- **测试方法**：`should_compute_dod_as_percentage_change`
- **对应需求**：「同比昨日 +100.00%」
- **前置**：今日 `150`，昨日 `100`
- **断言**：`dod == 50.00`（表示 +50.00%）
- **红在哪**：无实现
- **公式**：`(today - yesterday) / yesterday * 100`
- **陷阱**：
  - **正负号**：下降时返回负数（`today=80, yesterday=100 → -20.00`），不能返回 `20`。另写一条 `should_compute_negative_dod_when_declining`
  - **单位**：返回值是「百分数」（50.00），不是「倍数」（0.5）。前端 `.2f` 直接拼 `%`

---

## D19 · 昨日为 0 → 固定 100.00% ★★

- **测试方法**：`should_return_100_percent_dod_when_yesterday_is_zero`
- **对应需求**：**需求原文写死**「同比昨日 +100.00%」（示例数据昨日为 0 时显示 +100.00%）
- **前置**：今日 `3`，昨日 `0`
- **断言**：`dod == 100.00`（**恰好 100，不是 0，不是 999，不抛异常**）
- **红在哪**：实现里直接除 → `ArithmeticException` / `Infinity` → 测试红
- **陷阱**：这是全项目**唯一由需求原文明确规定的边界值**。数学上「从 0 增长」是无穷大，业务上定死 100%。四个指标（客户数/订单数/退款额/客单价）**都要遵守**，建议写成 `@ParameterizedTest` 跑 4 遍
- **为什么 ★★**：需求文档给了精确数字，这是最硬的验收断言，答辩时可以直接翻到需求那一行对照

---

# 功能点 4 · 消息通知（D20~D23，4 条）

## D20 · 按时间倒序

- **测试方法**：`should_order_messages_by_time_desc`
- **对应需求**：「消息按时间倒序展示，最新消息在最上方」
- **前置**：3 条消息，时间 `10:00 / 12:00 / 09:00`
- **断言**：返回顺序为 `12:00 → 10:00 → 09:00`
- **红在哪**：无 `ORDER BY create_time DESC` → 断言红
- **陷阱**：**排序要在 SQL 里做**，不要查出来再 `stream().sorted()` 反过来。分页时内存排序会出错

---

## D21 · 每条消息携带订单编号 ★

- **测试方法**：`should_attach_order_no_to_every_message`
- **对应需求**：「每条消息携带订单编号；点击单条消息，跳转对应订单详情页」
- **前置**：3 条消息，分别关联订单 `SO001/SO002/SO003`
- **断言**：每条返回对象都有 `orderNo` 字段且非空，值与桩数据一致
- **红在哪**：`tb_messages` 无 `order_no` 列 → Mapper/DTO 缺字段 → 编译红
- **陷阱**：一期末 shop 的 `Message` 实体**只有 title/content/msgType，没有 orderNo**，点击跳转实现不了。新库必须建这一列
- **为什么 ★**：这是需求里明确写「点击跳转」的前提字段，缺了功能直接断链

---

## D22 · 三类消息文案含单号

- **测试方法**：`should_build_three_message_types_with_order_no_in_text`
- **对应需求**：三种文案
  ```
  支付成功：用户 xxx 已支付成功，订单号 xxx，请及时处理
  退款申请：有买家发起退款，订单编号 xxx，请在 7 天内处理
  确认收货：订单 xxx 用户 xxx 已经确认收货
  ```
- **断言**（`@ParameterizedTest`，3 组）：
  - 支付成功消息 → `content` 含订单号、含买家标识
  - 退款申请消息 → `content` 含订单号、含「7 天内」
  - 确认收货消息 → `content` 含订单号
- **红在哪**：无实现 / 文案模板里漏了单号 → 断言红
- **陷阱**：
  - **文案是拼出来的还是存库的**？建议**存库**（`content` 字段直接存完整文本），不要运行时拼 —— 拼的话订单号变了历史消息也会跟着变
  - 断言用 `contains(...)` 匹配关键片段，**不要 `isEqualTo(整句)`**。需求文案里的「xxx」是占位符，整句断言会在改标点时莫名其妙红

---

## D23 · 消息隔离：只返回本店消息

- **测试方法**：`should_only_return_current_shop_messages`
- **对应需求**：权限段
- **前置**：shop 1 有 3 条，shop 2 有 5 条
- **断言**：返回 size = 3，`verify(messageMapper).selectByShop(eq(1L), any(), any())`，`never()` 出现 `2L`
- **红在哪**：SQL 缺 `shop_id` 条件 → 断言红
- **陷阱**：和 D09 是同一类断言，但**必须单独写一条**。消息表的隔离最容易漏 —— 它不像订单表那样有天然的 `shop_id` 业务语义，容易写成「查全部消息」

---

## 本批汇总

| 分组 | 编号 | 条数 |
|---|---|---|
| 状态卡片 | D01~D09 | 9 |
| 趋势图 | D10~D13 | 4 |
| 经营指标 | D14~D19 | 6 |
| 消息通知 | D20~D23 | 4 |
| | **合计** | **23** |

**预期：23 条全部先红。**

三条最硬的：
- **D19** 昨日为 0 → 100.00%（需求原文写死）
- **D14** 支付客户数必须 `DISTINCT`（桩数据要让订单数 ≠ 客户数）
- **D09** 跨店隔离（手测永远发现不了）
