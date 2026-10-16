# .tdd — 登录功能 TDD 练习材料

这个目录装着 `mall-auth` 模块**登录功能**的 TDD 实施材料：先做任务拆解，再按批次逐个走「红 → 绿 → 重构」。

## 四份文档，各回答一个问题

| 文档 | 回答的问题 | 什么时候看 |
|---|---|---|
| [`task/登录功能-任务拆解.md`](task/登录功能-任务拆解.md) | **要建什么** —— 6 个功能点 / 23 个功能上下文 / **87 个任务项**，每条都带「测试思路」 | 想知道需求被拆成了哪些可测的条目 |
| [`执行清单.md`](执行清单.md) | **下一步做什么** —— 把 87 项归并成 **22 个批次**，每批给出：新建哪些文件、测试方法名、断言要点、卡点、提交信息 | **动手时就看这份** |
| [`task/PROGRESS.md`](task/PROGRESS.md) | **做到哪了** —— 87 项打勾表，按里程碑 M1~M6 分组 | 每完成一个批次来打勾 |
| [`TDD-实操手册.md`](TDD-实操手册.md) | **为什么这么做** —— 命令速查、Java 里"红"的三种形态、断言翻译表、领域层 8 条纪律、坑清单、决策树 | 概念卡住时查 |

**入门加餐**：[`B1.1-Username-详解.md`](B1.1-Username-详解.md) —— 把第一个批次（`Username` 值对象）的 10 条用例**逐条展开**，
每条都写了「测试代码长什么样 / 跑出来该看到什么 / 生产代码此刻最少写成什么」。**第一次做 TDD 从这里开始。**

## 建议阅读顺序

```
1. 本文件（了解有什么）
2. B1.1-Username-详解.md  ← 先跑通一个完整循环，建立节奏感
3. 执行清单.md 的「第 0 步」+「批次总览表」
4. 边做边查：手册（概念）→ 拆解（某条任务项的细节）
```

## 推进路线

**不要按任务项编号 1→87 顺序做。** 真实路线是按层从内到外（共 22 个批次）：

```
M1 值对象(5批) → M2 聚合根+事件(2批) → M3 应用编排(7批)
→ M4 infrastructure(4批) → M5 web(1批) → M6 观察者(3批)
```

M1 全是不依赖任何框架的纯 Java，一个用例几十毫秒 —— 先在手感上建立"快速反馈"，
数据库和 HTTP 放到最后。

## 当前进度

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 值对象（`Username` / `RawPassword` / `PasswordHash` / `Token`） | 🔄 进行中：**B1.1 `Username` 9/10 条**（缺 `should_work_as_map_key`） |
| M2 ~ M6 | 聚合根、应用编排、基础设施、web、观察者 | ⬜ 未开始 |

详见 [`task/PROGRESS.md`](task/PROGRESS.md)。

## 配套的代码在哪

```
mall-auth/src/main/java/com/mall/auth/
├── domain/          领域模型与规则（纯 Java，零 Spring / 零 MyBatis）
│   ├── model/       ← 值对象、聚合根        【TDD 主战场】
│   ├── port/        出站端口接口
│   ├── event/       领域事件
│   └── exception/   领域异常
├── application/     用例编排
├── infrastructure/  端口的实现（BCrypt / MyBatis / TokenStore）
└── adapter/         web 与事件监听器

mall-auth/src/test/java/com/mall/auth/      ← 与上面镜像的结构
```

## 怎么跑测试

```bash
export JAVA_HOME=/d/jdk17
cd "/d/demo/commerce platform"

# 全模块
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test

# 只跑一个类 / 一个方法
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test -Dtest=UsernameTest
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test -Dtest=UsernameTest#should_reject_when_username_is_null
```

> 中文断言信息在 Windows GBK 控制台会乱码（只是显示问题）。
> 要看干净输出就读 `mall-auth/target/surefire-reports/com.mall.auth.*.txt`，那是 UTF-8 的。

## 归档

`task/archive/` 里是更早一版「注册功能」的任务拆解 —— 需求后来改成了登录，**仅留档，不参考**。
