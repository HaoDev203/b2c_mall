# mall-tdd

《项目需求v.1.1》的 88 条验收实现。**严格 TDD**：每个批次先写测试、跑红、再写实现。

## 与 shop 的关系

| | shop | mall-tdd |
|---|---|---|
| 定位 | 设计模式作业（**本次一行不改**） | 需求 v1.1 验收 |
| 端口 | 8081 | **8082** |
| 数据库 | `shop/shop.db` | **`mall-tdd/mall.db`**（独立） |
| 依赖 | nacos + redis | **都没有**（88 条是纯 Mockito 单元测试，不启 Spring） |

两者物理隔离：不互相引用，不影响对方编译。

> 为什么不能放成 shop 里的一个新包：同一个 Spring 上下文里
> `com.shop.service.DashboardService` 与 `com.mall.tdd.service.DashboardService`
> 会撞 Bean 名（默认都叫 `dashboardService`），`@RequestMapping("/dashboard")` 也会路径冲突。

## 建库（clone 后必做）

`*.db` 被 `.gitignore` 忽略，所以库不在仓库里，需要本地重建一次：

```bash
cd "D:/demo/commerce platform/mall-tdd"
python -c "import sqlite3;c=sqlite3.connect('mall.db');c.executescript(open('sql/mall-ddl.sql',encoding='utf-8').read());c.commit()"
```

DDL 全部 `IF NOT EXISTS`，可以反复执行。脚本位于 `sql/mall-ddl.sql`，
字段依据 `.tdd/task/需求拆解-00-总览.md` §六。

**建完库再灌演示数据**（B2 起需要真数据验证 SQL 语义）：

```bash
python -c "import sqlite3;c=sqlite3.connect('mall.db');c.executescript(open('sql/mall-demo-data.sql',encoding='utf-8').read());c.commit()"
```

数据用相对日期生成（今天 13 单 / 昨天 15 单 / 前天 10 单），**永远「今天/昨天」都有数据**，
不会隔天失效。脚本开头会清空 6 张表再插，可重复执行。里面埋了四个靶子：

| 靶子 | 目的 | 期望 |
|---|---|---|
| 分类「手机数码」两行不同 id | 验去重 | 导航只出 3 个名字 |
| 电饭煲 `stock=10` 且 `stock_warn=10` | 验 `<=` 含等号 | 预警 3 件（不是 2 件） |
| 限量球鞋 `stock_warn IS NULL` | 验 NULL 不预警 | 它不计入预警 |
| 今天 9 单已支付 / 只有 7 个 `user_id` | 验 `COUNT(DISTINCT)` | 客户数 7 ≠ 订单数 9 |

## 跑测试

```bash
export JAVA_HOME=/d/jdk17
cd "/d/demo/commerce platform"
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-tdd test -Duser.language=en -Duser.country=US
```

- `-Duser.language=en`：中文编译错误在 GBK 控制台会乱码，切英文提示才看得清
- 覆盖率报告自动产出：`mall-tdd/target/site/jacoco/index.html`
- 中文断言信息看 `mall-tdd/target/surefire-reports/*.txt`（UTF-8）

## B0 的红绿证据链

B0 是骨架批次，它的价值不是"跑通"，而是**证明测试真的在跑**。以下 4 次都是实测输出：

| # | 动作 | 输出 | 说明 |
|---|---|---|---|
| 1 | 只建骨架（pom + 启动类 + yml） | `No tests to run.` + `BUILD SUCCESS` | ★ **假绿**：一个测试都没跑也叫 SUCCESS，必须越过 |
| 2 | 写 `MallDbSchemaProbeTest` 3 条 | `Tests run: 3, Failures: 3`<br>`tb_order 缺少字段 order_type，实际列：[]` | 红在「库是空的」，不是别的原因 |
| 3 | 执行 `sql/mall-ddl.sql` | `Tests run: 3, Failures: 0` | 转绿，**没有改一行测试** |
| 4 | 写 `UserContextProbeTest` 2 条 | `COMPILATION ERROR`<br>`找不到符号 类 UserContext` | 红在「类还不存在」，这才是真红 |
| 5 | 写 `LoginUser` + `UserContext` | `Tests run: 5, Failures: 0`<br>`Loading execution data file ... jacoco.exec` | 全绿 + 覆盖率生效 |

对应 6 个 commit（5 个 TDD 步骤 + 1 个 docs），**test commit 永远早于 feat commit**。

> **一个诚实的说明（答辩前必看）**
>
> 第 2 步的「库结构探针红」只能在**库还不存在**时复现：`mall.db` 被 `.gitignore` 忽略，
> 它属于环境而不是代码。本地已经有库时，`git checkout b11bf89` 再跑反而是绿的。
> 要复现就把库临时挪开：`mv mall.db mall.db.keep`，跑完 `mv` 回来。
>
> 第 4 步的「UserContext 编译红」**与库无关，任何环境都能复现**（实测过：
> checkout 到 `e3c97e4`，`common/` 目录为空，`mvn -pl mall-tdd test` 直接
> `COMPILATION ERROR` + `BUILD FAILURE`）。**答辩演示用这一步，不要用库探针。**

## 两个已踩过的坑

**① 根 pom 的 argLine 会吃掉 jacoco。**
根 pom 在 surefire 里写死 `<argLine>-Dfile.encoding=UTF-8</argLine>`，
这会**整体覆盖** jacoco prepare-agent 注入的 `-javaagent`。
表现极具迷惑性：测试照跑照绿，但日志里只有一句
`Skipping JaCoCo execution due to missing execution data file`，覆盖率永远 0。
本模块用 `@{argLine} -Dfile.encoding=UTF-8` 延迟求值修掉，**没有改根 pom**（改它会波及 shop）。

**② Mock 测不出 SQL 语义。**
`MallDbSchemaProbeTest` 是唯一连真库的测试，全程只读（`sqlite_master` / `PRAGMA`），
不写数据。B2 起出现了 `COUNT(DISTINCT user_id)`、`stock <= stock_warn`、
`ORDER BY create_time DESC` 这类**只能靠真库验证**的语义，做法是用一次性探针
（`dependency:build-classpath` + 独立 MyBatis `SqlSessionFactory` 直连 `mall.db`）
跑一遍真实 SQL，把结果抄进提交说明，探针本身不入库。

B2 实测结果：今天 9 单已支付 → `COUNT(DISTINCT)` 得 **7**（`COUNT(*)` 会得 9）；
库存预警 **3** 件（含 `10<=10` 的等号边界，`stock_warn IS NULL` 的不算）；
消息 3 条倒序 `18:00 → 09:15 → 昨天 13:00`；空日期 `SUM` 返回 **0.0** 而不是 null；
干扰店金额 49995 分文未混入。

## 目录

```
mall-tdd/
├── mall.db                本地库（不入库）
├── sql/
│   ├── mall-ddl.sql       建表脚本（6 张表 + 6 个索引，需求全字段）
│   └── mall-demo-data.sql 演示数据（相对日期，可重复执行）
└── src/
    ├── main/java/com/mall/tdd/
    │   ├── MallTddApplication.java
    │   ├── common/        LoginUser / UserContext / Result
    │   ├── entity/        Product / Category / Message
    │   ├── dto/           MallProductVO / TrendVO / MetricVO / MetricsVO / MessageVO / HourAmount
    │   ├── mapper/        ProductMapper / CategoryMapper / OrderMapper / MessageMapper（注解 SQL）
    │   └── service/       MallProductService / MallCategoryService / DashboardService
    └── test/java/com/mall/tdd/
        ├── common/        UserContextProbeTest（2）
        ├── probe/         MallDbSchemaProbeTest（3）
        └── service/       DashboardServiceTest（32）/ MallProductServiceTest（8）
                           / MallCategoryServiceTest（1）
```

## 进度

| 批次 | 条数 | 状态 |
|---|---|---|
| B0 骨架 + 探针 | 5 探针 | ✅ `Tests run: 5` |
| B1 C 端商品列表 | 9 | ✅ `Tests run: 14` |
| B2 店铺 Dashboard | 23 | ✅ `Tests run: 46` |
| B3 C 端订单 / B4 商品管理 / B5 订单查询 | 56 | ⬜ |

**B1 的变异验证**：删掉排序白名单、把 `putIfAbsent` 改成 `put` 后重跑，
`Failures: 5, Errors: 1` —— C07（注入）与 C02（去重保留首条）都红在断言上。
说明这 9 条不是「写完就算绿」的空壳测试。

**B2 的变异验证**：一次改坏 5 处（同比去掉昨日为 0 的分支、客户数改用订单数、
趋势图不补 24 点、卡片多塞一个 `tradeFailed`、消息加内存排序），
`Failures: 6, Errors: 2`，红的正好是 D02 / D10 / D11 / D12 / D13 / D14 / D19 / D20b，
其余 24 条不受影响 —— 每条测试都咬在自己那个行为上。

**Controller 暂缓**：验收全在 Service 层，Controller 没有对应条目，
先写就是无人守护的代码。5 个页面的接口在 B5 之后一次性补齐。
