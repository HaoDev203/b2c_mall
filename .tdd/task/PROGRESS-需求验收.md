# PROGRESS · 需求验收（88 条）

> 依据 `.tdd/task/需求拆解-00-总览.md` 与 5 份页面明细。
> 状态图例：`[ ]` 未开始 · `[~]` 已写测试（预期红）· `[x]` 转绿并提交
>
> 每批必须**先写全部测试跑红**，再写实现。`[x]` 只在「测试绿 + 两个 commit 已提交」后才打。

---

## 总览

| 批次 | 页面 | 🔴 条数 | 已完成 | 状态 |
|---|---|---|---|---|
| B0 | 模块骨架 + DDL + 探针 + jacoco | 5 探针 | 5 / 5 | ✅ |
| B1 | C 端商品列表 | 9 | 9 / 9 | ✅ |
| B2 | 店铺 Dashboard | 23 | 23 / 23 | ✅ |
| B3 | C 端订单管理 | 9 | 9 / 9 | ✅ |
| B4 | 店铺商品管理 | 23 | 23 / 23 | ✅ |
| B5 | 订单查询 | 24 | 24 / 24 | ✅ |
| | **合计** | **88** | **88 / 88** | ✅ |

**全部 🔴 先红**（新模块从零，无既有代码可锁）。

> **合计口径**：88 = B1~B5 的需求条数（9+23+9+23+24），**不含 B0 的 5 个探针** —— 探针是环境验证，单独记在 B0 行（5/5 ✅）。

---

## B0 · 模块骨架（已完成）

| 编号 | 内容 | 验收标准 | 状态 |
|---|---|---|---|
| B0.1 | 建 `mall-tdd` 模块 + `mall.db` + 依赖 + jacoco | 跑出 `Tests run:` 与 `jacoco/index.html` | [x] |

实际落地 **5 条**探针，不是最初计划的 1 条：一个方法里塞「6 张表 + 12 个字段」，
红了定位不到是哪一条，违反「一条测试一个行为」。

| 测试类 | 条数 | 验什么 |
|---|---|---|
| `MallDbSchemaProbeTest` | 3 | 6 张业务表 / `tb_product` 的 6 个需求新增字段 / `tb_order` 的 6 个需求新增字段 |
| `UserContextProbeTest` | 2 | set 后可读；clear 后为 null（防 Tomcat 线程复用串号） |

当前状态：`Tests run: 5, Failures: 0`，日志出现 `Loading execution data file ... jacoco.exec`
（不是 `Skipping JaCoCo execution`），报告在 `mall-tdd/target/site/jacoco/index.html`。

红绿证据链见 `mall-tdd/README.md`；6 个 commit 里 **test 始终早于对应的 feat**。

---

## B1 · C 端商品列表（9 条）

测试类：`MallProductServiceTest` / `MallCategoryServiceTest`

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| C01 | `should_only_return_on_sale_products` | 只返回 `on_sale`，过滤在 SQL 里 | [x] |
| C02 | `should_dedupe_category_navigation_by_name` ★ | 同名只留首条，`LinkedHashMap` | [x] |
| C03 | `should_return_all_on_sale_products_when_category_is_absent` | `categoryId=null` 不当 0 传 | [x] |
| C04 | `should_filter_products_by_category_id` | 分类 + 上架两个条件同时生效 | [x] |
| C05 | `should_sort_by_sales_desc` | 销量倒序 | [x] |
| C06 | `should_sort_by_price` | 价格参数原样下发 | [x] |
| C07 | `should_fall_back_to_default_sort_when_param_is_illegal` ★★ | 排序白名单防注入 | [x] |
| C08 | `should_return_all_card_fields` | 主图/名称/售价/销量 4 字段非空 | [x] |
| C09 | `should_not_expose_detail_of_off_sale_product` | 详情二次校验状态 | [x] |

**B1 已完成**（2026-10-23）。`mvn -pl mall-tdd test` → `Tests run: 14, Failures: 0`
（B0 的 5 条探针 + B1 的 9 条）。

> **变异验证（证明测试有牙齿）**：故意把 `resolveSort` 的白名单删掉、
> 把 `putIfAbsent` 改成 `put`，重跑得到 `Failures: 5, Errors: 1` ——
> C07（注入）、C02（去重保留首条）都准确红在了断言上，不是假绿。
> 验证完已还原，工作区干净。
>
> **B1 只交付了 Service，没写 Controller**：9 条验收全部落在 Service 层，
> Controller 没有对应验收条目，先写就是无人守护的代码。5 个页面的接口
> 统一在 B5 之后一次性补齐（薄适配层，只做参数透传）。

---

## B2 · 店铺 Dashboard（23 条）

测试类：`DashboardServiceTest`

### 状态卡片

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| D01 | `should_fail_without_querying_when_shop_id_is_absent` | shopId 为 null 时 `never()` 查库 | [x] |
| D02 | `should_return_six_status_card_keys` | **恰好** 6 个 key，不多不少 | [x] |
| D03 | `should_count_pending_pay_orders` | 桩值 `5L` | [x] |
| D04 | `should_count_pending_ship_orders` | 桩值 `12L`（需求示例数） | [x] |
| D05 | `should_count_pending_receive_orders` | 桩值 `2L` | [x] |
| D06 | `should_count_completed_orders` | 桩值 `24L`，状态映射要对 | [x] |
| D07 | `should_count_refund_and_after_sale_orders` | 桩值 `1L` | [x] |
| D08 | `should_count_stock_warning_products` | `stock <= stock_warn`，NULL 不预警 | [x] |
| D09 | `should_not_mix_other_shop_data` | 每次调用都是 `eq(SHOP_ID)` | [x] |

### 趋势图

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| D10 | `should_return_24_points_for_trend` | 两条曲线各 24 点 | [x] |
| D11 | `should_sum_today_paid_amount_by_hour` | 同小时多笔**累加**不是覆盖 | [x] |
| D12 | `should_sum_yesterday_paid_amount_by_hour` | 两次独立查询 | [x] |
| D13 | `should_fill_zero_for_hours_without_orders` | 补 `0` 不补 `null` | [x] |

### 经营指标

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| D14 | `should_count_distinct_paid_customers` ★ | `COUNT(DISTINCT user_id)`，桩数据订单数≠客户数 | [x] |
| D15 | `should_count_paid_orders` | `pay_time IS NOT NULL` 不用 status | [x] |
| D16 | `should_sum_refund_amount` | 无退款返回 `0.00` 不返回 null | [x] |
| D17 | `should_compute_average_order_value` | 总金额÷订单数，分母 0 返回 `0.00` | [x] |
| D18 | `should_compute_dod_as_percentage_change` | `(今日-昨日)/昨日×100`，下降为负 | [x] |
| D19 | `should_return_100_percent_dod_when_yesterday_is_zero` ★★ | **昨日为 0 → 恰好 100.00** | [x] |

### 消息通知

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| D20 | `should_order_messages_by_time_desc` | SQL 里 `ORDER BY create_time DESC` | [x] |
| D21 | `should_attach_order_no_to_every_message` ★ | 每条都有 `orderNo` 非空 | [x] |
| D22 | `should_build_three_message_types_with_order_no_in_text` | 三类文案含单号 | [x] |
| D23 | `should_only_return_current_shop_messages` | 消息表隔离 | [x] |

---

## B3 · C 端订单管理（9 条）

测试类：`MallOrderServiceTest`

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| U01 | `should_only_return_current_user_orders` ★ | userId 只从 UserContext 取 | [x] |
| U02 | `should_return_count_per_status_tab` ★ | 「全部」= **独立统计**（不是 4 个之和），桩值须不一致 | [x] |
| U03 | `should_search_by_order_no_exactly` ★ | 精确 `=`，前缀查 0 条（需真 SQL 验） | [x] |
| U04 | `should_return_all_order_card_fields` | 9 字段非空，含 `shopName` | [x] |
| U05 | `should_soft_delete_order_not_physically` ★★ | `never()` 物理删除 + 反射断言无 `delete*` | [x] |
| U06 | `should_keep_order_visible_in_admin_after_buyer_deletes` ★★ | 后台 `selectAdminPage` **不带** `is_deleted` | [x] |
| U07 | `should_not_delete_other_users_order` | 越权影响 0 行 | [x] |
| U08 | `should_hide_deleted_orders_from_buyer_list` | 列表 + 总数同时过滤 | [x] |
| U09 | `should_return_buy_now_info_for_purchasable_product` | 已下架/无库存不可购买 | [x] |

---

## B4 · 店铺商品管理（23 条）

测试类：`ProductQueryServiceTest`（P22/P23 导出导入也在这个类里）

### 顶部筛选

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| P01 | `should_search_by_name_fuzzy` | 模糊，`%` 由 SQL 拼 | [x] |
| P02 | `should_filter_by_goods_type` | 用 `goods_type`，**不是** `product_type` | [x] |
| P03 | `should_filter_by_category_kind` | 用 `category_kind` | [x] |
| P04 | `should_filter_by_status` | 非法状态返回失败不静默 | [x] |
| P05 | `should_filter_by_multiple_delivery_types` ★ | `<foreach>` + 逗号边界整词匹配，`#{}` 不是 `${}` | [x] |
| P06 | `should_filter_by_category_id` | 分类 ≠ 类别 | [x] |
| P07 | `should_apply_all_six_filters_together` | 一次调用 6 个参数全在 | [x] |

### 列表展示

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| P08 | `should_cap_page_size_and_return_correct_total` | size 上限（防 DoS） | [x] |
| P09 | `should_sort_by_sort_field_desc` | 数字越大越靠前（DESC） | [x] |
| P10 | `should_return_all_twelve_list_fields` | 12 字段非空 | [x] |
| P11 | `should_return_real_and_inflated_sales_separately` ★ | 真实销量 ≠ 含注水 | [x] |
| P12 | `should_return_warehouse_and_store_stock_separately` | 桩值须不等 | [x] |

### 删除

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| P13 | `should_reject_delete_when_product_has_orders` ★★ | 先查明细再删 + `never()` 删除 | [x] |
| P14 | `should_allow_delete_when_product_has_no_order` | P13 的反例（防「永远失败」） | [x] |
| P15 | `should_not_delete_other_shops_product` | 越权最先拦，不查订单引用 | [x] |

### 批量设置

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| P16 | `should_change_status_per_item_independently` ★ | 逐条独立，**不加 `@Transactional`** | [x] |
| P17 | `should_report_partial_failure_without_rolling_back` | 返回 `{total,success,failed,results}` | [x] |
| P18 | `should_batch_update_delivery_types` | 多选序列化存库 | [x] |
| P19 | `should_batch_update_category_id` | — | [x] |
| P20 | `should_batch_update_sort` | 统一值 | [x] |
| P21 | `should_batch_update_stock` | 仓库/门店两字段 | [x] |

### 导出导入

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| P22 | `should_export_current_filtered_result` | 行数=筛选结果，忽略分页 | [x] |
| P23 | `should_import_by_product_code_upsert` | 按 `product_code` 判新增/更新 | [x] |

---

## B5 · 订单查询（24 条）

测试类：`OrderQueryServiceTest`（O24 导出也在这个类里）

### 顶部筛选

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| O01 | `should_search_by_order_no_exactly` ★★ | 精确 `=`，前缀查 0 条（需真 SQL 验） | [x] |
| O02 | `should_filter_by_order_type` | `order_type` 列 | [x] |
| O03 | `should_filter_by_order_category` | `order_category` 列（≠ 类型） | [x] |
| O04 | `should_filter_by_status_including_group_pending` ★ | 状态枚举含「待成团」，共 7 个 | [x] |
| O05 | `should_search_by_receiver_name_fuzzy` | 模糊（与 O01 相反） | [x] |
| O06 | `should_search_by_receiver_phone_fuzzy` | 模糊（一期末是精确） | [x] |
| O07 | `should_filter_by_pay_type` | 英文枚举存库 | [x] |
| O08 | `should_filter_by_after_sale_status` | 「暂无售后」映射 `IS NULL` | [x] |
| O09 | `should_filter_by_logistics_type` | 物流类型 ≠ 配送方式 | [x] |
| O10 | `should_filter_by_store_name` | 精确 | [x] |
| O11 | `should_filter_by_create_time_range` | **闭区间**，`< date(end,'+1 day')`，不在列上套函数 | [x] |

### 快捷时间与组合

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| O12 | `should_resolve_quick_date_range` | 今日/昨日/近7/近30，注入固定 `Clock` | [x] |
| O13 | `should_apply_all_filters_together` | 11 参数一次调用全在 | [x] |

### 状态标签栏

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| O14 | `should_return_count_for_seven_tabs` ★ | 7 Tab，`all` = 独立 `COUNT(*)`（求和会漏 REFUNDING） | [x] |

### 每行展示

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| O15 | `should_compute_total_quantity_from_order_items` ★ | N = 数量之和，不是行数 | [x] |
| O16 | `should_mask_receiver_phone` ★ | `138****8000`，后端脱敏 | [x] |
| O17 | `should_return_status_tags` | 5 种标签，可多值 | [x] |

### 发货

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| O18 | `should_write_tracking_no_when_shipping` ★ | **一条 UPDATE 同时写状态与单号**，不留中间态 | [x] |
| O19 | `should_reject_ship_when_status_is_not_pending_ship` | 6 个非待发货状态全拒 | [x] |
| O20 | `should_batch_ship_each_order_independently` | 逐条独立 | [x] |
| O21 | `should_report_partial_failure_without_rollback` | results 顺序与入参一致 | [x] |

### 数据隔离与导出

| 编号 | 测试方法 | 断言要点 | 状态 |
|---|---|---|---|
| O22 | `should_not_return_other_shops_orders` | 所有分支都带 `shop_id` | [x] |
| O22b | `should_cover_shop_isolation_in_every_sql` | 7 个带 shopId 的方法逐个断言隔离（变异验证逼出来的） | [x] |
| O23 | `should_reject_ship_and_detail_of_other_shop_order` | 越权返回失败，不发任何 UPDATE | [x] |
| O24 | `should_export_current_filtered_orders` | 行数=筛选结果，手机号仍脱敏 | [x] |

---

## 提交节奏

每批两个 commit，**test 必须先于 feat**：

```bash
git add mall-tdd/src/test
git commit -m "test(mall-tdd): B1 九条验收测试（红）"
git add mall-tdd/src/main
git commit -m "feat(mall-tdd): C 端商品列表（B1）"
```

加列 / 建表这类改动单独一个 `chore` commit，不与 test/feat 混在一起（**改实体 = 编译红，会污染整批**）。

---

## 五个会卡住的坑（提前记住）

1. **`UserContext` 是静态 ThreadLocal** → `@BeforeEach` 手动 set、`@AfterEach` clear，**不要 `mockStatic`**（会和参数化测试打架）。
2. **原始值与匹配器不能混用** → `when(m.count(SHOP_ID, anyString()))` 抛 `InvalidUseOfMatchersException`，必须 `eq(SHOP_ID)`。
3. **Mockito 严格桩** → 不要显式 `thenReturn(null)`，会被判 `UnnecessaryStubbing`。
4. **`long` 返回类型桩值要带 `L`** → `thenReturn(2L)` 不是 `thenReturn(2)`。
5. **Mock 测不出 SQL 语义** → O01（精确 vs LIKE）、O06、O11（闭区间）、C02（DISTINCT 去不掉）这几条**必须配真 SQL 探针**：`UnpooledDataSource` + `Configuration` + `openSession()` 不自动提交 + 最后 `rollback()`。
