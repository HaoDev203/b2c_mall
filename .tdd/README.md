# .tdd — TDD 实施材料

本目录有**两条独立线**，别混着看：

| 线 | 对应模块 | 状态 | 文档 |
|---|---|---|---|
| **A 线：需求验收**（当前主线） | 待建 `mall-tdd` | 拆解完成，待确认后开工 | 本文件下半部分 |
| B 线：登录功能练习 | `mall-auth` | 暂停（需求文档里没有登录功能） | 本文件末尾「归档区」 |

---

# 主线 · 需求验收（《项目需求v.1.1》→ 88 条）

## 文档地图

| 文档 | 回答的问题 |
|---|---|
| [`需求拆解与TDD实现计划.md`](需求拆解与TDD实现计划.md) | **怎么走** —— 批次顺序、每批 5 步流程、**答辩演示脚本**、风险与应对 |
| [`task/需求拆解-00-总览.md`](task/需求拆解-00-总览.md) | **全局约定** —— 索引、接口契约、数据口径、表字段、不可测清单 |
| [`task/需求拆解-01-C端商品列表.md`](task/需求拆解-01-C端商品列表.md) | B1，9 条 |
| [`task/需求拆解-02-店铺Dashboard.md`](task/需求拆解-02-店铺Dashboard.md) | B2，23 条 |
| [`task/需求拆解-03-C端订单管理.md`](task/需求拆解-03-C端订单管理.md) | B3，9 条 |
| [`task/需求拆解-04-店铺商品管理.md`](task/需求拆解-04-店铺商品管理.md) | B4，23 条 |
| [`task/需求拆解-05-订单查询.md`](task/需求拆解-05-订单查询.md) | B5，24 条 |
| [`task/PROGRESS-需求验收.md`](task/PROGRESS-需求验收.md) | **做到哪了** —— 88 条打勾表 + 5 个会卡住的坑 |

**每条任务项都带三样东西**：可测行为、**测试方法名**、**「红在哪」**（跑红时用来核对是否红在预期位置）。
红的位置不对 = 测试写错了 → 改测试，不改实现。

## 阅读顺序

```
1. 本文件（了解有什么）
2. task/需求拆解-00-总览.md      ← 先看数据口径与不可测清单，避免后面反复踩
3. 需求拆解与TDD实现计划.md      ← 看批次顺序与答辩脚本
4. task/需求拆解-01（B1，最短一批）← 走通一个完整循环，建立节奏
5. 边做边打勾：task/PROGRESS-需求验收.md
```

## 进度

`0 / 88`，全部 🔴 先红（新模块从零，没有既有代码可以锁）。

| 批次 | 页面 | 条数 | 状态 |
|---|---|---|---|
| B0 | 模块骨架 + DDL + 探针 + jacoco | 1 | ⬜ |
| B1 | C 端商品列表 | 9 | ⬜ |
| B2 | 店铺 Dashboard | 23 | ⬜ |
| B3 | C 端订单管理 | 9 | ⬜ |
| B4 | 店铺商品管理 | 23 | ⬜ |
| B5 | 订单查询 | 24 | ⬜ |

## 怎么跑测试（B0 建好后）

```bash
export JAVA_HOME=/d/jdk17
cd "/d/demo/commerce platform"

# 整批
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-tdd test

# 只跑一个类 / 一个方法
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-tdd test -Dtest=MallProductServiceTest
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-tdd test -Dtest=MallProductServiceTest#should_only_return_on_sale_products

# 看编译错误（中文会乱码，强制英文）
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-tdd test -Duser.language=en -Duser.country=US

# 覆盖率
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-tdd test jacoco:report
# → mall-tdd/target/site/jacoco/index.html
```

---

# 归档区 · 登录功能练习（B 线）

> **需求文档里没有登录功能。** 「登录」在原文出现 5 次，全是「登录商家账号才可访问」这类**权限前置条件**，不是功能需求。
> 这条线是有价值的架构练习（值对象、聚合根、领域事件、观察者），但**不是本次交付物**，暂停。

| 文档 | 内容 |
|---|---|
| [`task/登录功能-任务拆解.md`](task/登录功能-任务拆解.md) | 87 项任务，6 功能点 / 23 功能上下文 |
| [`执行清单.md`](执行清单.md) | 87 项归并成 22 个批次 |
| [`task/PROGRESS.md`](task/PROGRESS.md) | 87 项打勾表（停在 `0 / 87`，**未动**） |
| [`TDD-实操手册.md`](TDD-实操手册.md) | 方法论：命令速查、红的三种形态、断言翻译表、坑清单 |
| [`B1.1-Username-详解.md`](B1.1-Username-详解.md) | 值对象逐条展开、手工变异测试 |
| [`B1.2-B1.3-密码值对象-详解.md`](B1.2-B1.3-密码值对象-详解.md) | 密码领域的三处「反着来」、一条会永久假绿的 `toString` 测试 |
| [`task/archive/注册功能-任务拆解.md`](task/archive/注册功能-任务拆解.md) | 更早一版，仅留档 |

**方法论那两份（`TDD-实操手册.md`）主线也能用** —— 里面的「红的三种形态」「断言翻译表」对 88 条一样适用，只是代码落点从 `mall-auth` 换成了 `mall-tdd`。

跑这条线的测试：
```bash
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test
# → Tests run: 16, Failures: 0
```
