# mall-auth · TDD 实操手册（登录功能）

> 这份文档只描述**你该怎么做**，不包含任何已写好的业务代码。  
> 任务拆解（87 项）已经产出，**动手请翻 `执行清单.md`**（22 个批次，动作级）；本文件负责解释"为什么这么做"。

---

## 0. 开工基线（已完成，不要重做）

| 项        | 状态                                                                  |
| -------- | ------------------------------------------------------------------- |
| 分支       | `feature/tdd-mall-auth`（从 `main` 拉出）                                |
| 模块       | `mall-auth`（`com.zh.b2cmall.auth`，包名 `com.mall.auth`，端口 8082）       |
| 测试依赖     | 根 POM 已统一管理 `spring-boot-starter-test`                              |
| surefire | 已钉死 `3.2.5` + `-Dfile.encoding=UTF-8`                               |
| 基建验证     | 红灯（`Tests run: 2, Failures: 1`）、绿灯（`Tests run: 1, Failures: 0`）均已实测 |
| 现有测试     | `TddWiringSmokeTest`（哨兵，保留即可）                                       |
| 包骨架       | `domain/{model,port,event,exception}`、`application`、`infrastructure/{security,token,persistence}`、`adapter/{web,listener}` 已建好（`src/test` 有镜像结构） |
| 任务拆解     | 已产出：`task/登录功能-任务拆解.md`（87 项）+ `task/PROGRESS.md`（打勾表）        |
| 执行顺序     | 见 `执行清单.md`（22 个批次，动作级）                                          |
| 未提交      | 改动还在工作区。**先自己提交一次作为基线**，之后每个批次一个提交                            |

```bash
cd "/d/demo/commerce platform"
git add pom.xml mall-auth .tdd
git commit -m "chore(mall-auth): 登录功能 TDD 基线（模块 + 测试基建 + 包骨架 + 任务拆解）"
```

---

## 1. 命令速查

**跑全模块测试**（最常用）

```bash
export JAVA_HOME=/d/jdk17
cd "/d/demo/commerce platform"
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test
```

**只跑一个测试类**（TDD 循环里你会用几百次）

```bash
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test -Dtest=UsernameTest
```

**只跑一个方法**

```bash
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test -Dtest=UsernameTest#should_throw_when_username_is_blank
```

**IDEA 里**：打开测试类，点方法左边的绿色三角 —— 比命令行快，推荐日常用 IDEA 跑，命令行只用来做最终验证。

> 中文断言信息在 Windows GBK 控制台会乱码（只是显示问题，不影响结果）。要看干净输出就读  
> `mall-auth/target/surefire-reports/com.mall.auth.*.txt`，那是 UTF-8 的。

---

## 2. 先搞清楚：Java 里的"红"有三种

TDD 说的"红"不只有断言失败。Java 是静态类型语言，**第一次红往往是编译不过**，这也是红，而且是正常流程。

| 层次    | 现象                                                | 说明                 | 你该做什么                                                                    |
| ----- | ------------------------------------------------- | ------------------ | ------------------------------------------------------------------------ |
| ① 编译红 | `cannot find symbol` / `class Username not found` | 生产代码还不存在，测试类引用了空气  | 建**空壳**让它能编译（方法体 `throw new UnsupportedOperationException()` 或返回 `null`） |
| ② 断言红 | `Tests run: 3, Failures: 3`                       | 能跑了，但行为没实现         | 写**最小实现**让它变绿                                                            |
| ③ 边界红 | 前面的绿了，新加一个边界用例红                                   | 你在补边界（空值、超长、越界、并发） | 继续循环                                                                     |

**核心纪律：每次只让"一个"用例从红变绿。** 一次写 5 个测试再一起实现，那不是 TDD，是"先写测试"。

---

## 3. 任务拆解（**已产出**，此节转为存档）

这一步已经做完了，产出在 `task/登录功能-任务拆解.md`（87 个任务项 / 23 个上下文 / 6 个功能点）。
下面保留"怎么喂 md"的原文，方便你以后对**别的功能**（比如注册、优惠券）重跑一遍这套流程。

### 3.1 建目录（已建）

```bash
cd "/d/demo/commerce platform"
mkdir -p .tdd/task
```

### 3.2 把 md 的【输入您的需求】换成这段

```text
在 b2c 商城的 mall-auth 服务中实现前台买家的「用户名 + 密码」登录，具体包括：
1. 用户通过 HTTP 接口提交用户名与密码；
2. 用户名/密码为空、或格式非法（长度、字符集）时，直接拒绝并给出明确提示；
3. 「用户名不存在」「密码错误」「账号被禁用」三种情况必须能区分，给出不同提示；
4. 登录必须用 BCrypt 比对密码，绝不比对明文；比对失败不得签发令牌；
5. 校验通过后签发一个不透明 token（默认 30 分钟有效、可主动失效）并返回给调用方；
6. 登录成功后需要记录登录日志、更新最后登录时间、累加登录次数（副作用，用观察者/事件解耦）；
7. 任何失败路径都不得签发令牌、不得写日志、不得改动登录计数。
如有不清晰的内容使用交互式问答让我解释。
```

### 3.3 把 md 的【输入您的架构】换成这段

```text
Spring Cloud Alibaba 微服务项目 b2c_mall 中的独立模块 mall-auth
（Spring Boot 2.7.18 / Java 8，端口 8082）。模块内采用四层结构：

- adapter/web    ：LoginController + LoginRequest DTO，只做协议转换，不含业务判断
- application    ：LoginApplicationService 编排用例（认证、签发令牌、发布事件、调用出站端口）
- domain         ：领域模型与规则（Username、RawPassword、PasswordHash、Token、User 聚合根、
                   UserStatus；UserRepository / PasswordEncoder / TokenIssuer / TokenStore /
                   TimeProvider 出站端口；LoginRejectedException 领域异常），
                   纯 Java，零 Spring / 零 MyBatis 依赖
- infrastructure ：实现 domain / application 定义的出站端口（MyBatisUserRepository、
                   BCryptPasswordEncoderAdapter、InMemoryTokenStore）

依赖方向：adapter、infrastructure → application → domain。
密码以 BCrypt 哈希存储，登录时用 matches 比对；令牌是不透明随机串，并入微服务后存 Redis。
```

### 3.4 已产出的文件

```text
.tdd/
├── TDD-实操手册.md          ← 本文件（为什么这么做）
├── 执行清单.md              ← 22 个批次，动作级：先做哪个文件、跑什么命令、看到什么算对
├── B1.1-Username-详解.md    ← 第一批 10 行的逐条展开（第一次做 TDD 从这里开始）
└── task/
    ├── 登录功能-任务拆解.md    ← 要建什么（6 功能点 / 23 上下文 / 87 任务项，每条带测试思路）
    ├── PROGRESS.md          ← 做到哪了（打勾表）
    └── archive/             ← 上一轮注册版拆解，已被登录版取代，仅留档不参考
```

**开工前请先读 `执行清单.md` 的"第 0 步"和批次总览表** —— 它把 87 个任务项归并成 22 个批次，
你手边的单位是"一个测试类"，不是"一个任务项"。

### 3.5 拆解质量自查（不合格就重跑）

1. **每个「功能上下文」都点名了具体组件** —— 出现 "domain 层 - 值对象 `Token`" 这种字样，而不是含糊的"业务逻辑层"。
2. **每个任务项都能被一个测试覆盖** —— 如果你觉得"这没法测"，说明它还太大，继续拆。
3. **测试思路写的是断言，不是动作** —— "断言返回 500" 合格；"验证登录" 不合格。
4. **边界被显式列出** —— 空值、长度、类型、越界、重复、时序。
5. **最后一节「架构与重构思考」有实质内容** —— 至少要指出 1 个耦合点和 1 个重构方向。

---

## 4. 「测试思路 → @Test」翻译表

你的 md 只产出**断言思路**（自然语言），下面这张表负责把它翻成能跑的 Java。这是整个流程里最容易卡住的一步。

| md 里的测试思路         | Java 断言（AssertJ，推荐）                                                                           |
| ----------------- | --------------------------------------------------------------------------------------------- |
| 断言返回失败，code = 500 | `assertThat(result.getCode()).isEqualTo(500)`                                                 |
| 断言失败提示为"密码错误"     | `assertThat(result.getMsg()).isEqualTo("密码错误")`                                               |
| 断言不抛异常            | `assertThatCode(() -> ...).doesNotThrowAnyException()`                                        |
| 断言抛出业务异常且提示明确     | `assertThatThrownBy(() -> ...).isInstanceOf(BizException.class).hasMessageContaining("密码错误")` |
| 断言返回的对象字段正确       | `assertThat(result.token().value()).hasSize(32)`                                              |
| 断言某方法被调用一次        | `verify(userRepository, times(1)).findByUsername(any())`                                      |
| 断言某方法**没有**被调用    | `verify(tokenIssuer, never()).issue(any())`                                                   |
| 断言两个方法的**先后顺序**    | `InOrder inOrder = inOrder(passwordEncoder, tokenIssuer); inOrder.verify(...); inOrder.verify(...);` |
| 断言传给某个方法的**入参内容**  | `ArgumentCaptor<LoginUser> captor = ArgumentCaptor.forClass(LoginUser.class); verify(tokenIssuer).issue(captor.capture()); assertThat(captor.getValue().getUserId()).isEqualTo(100L);` |
| 断言集合内容            | `assertThat(list).hasSize(2).extracting(Item::name).containsExactly("a","b")`                 |
| 断言集合为空/非空         | `assertThat(list).isEmpty()` / `.isNotEmpty()`                                                |

**命名规范**（统一了才好读）：`should_<期望结果>_when_<前提条件>`  
例：`should_return_token_when_password_matches`

---

## 5. 第一个红绿重构循环（手把手，照做一遍就懂了）

> ⚠️ **本节是「一次性看完的浓缩示例」（4 个用例）**，用来说明流程的完整形状。
> 真正动手做 B1.1 时，请照 **`B1.1-Username-详解.md`** —— 那里把清单的 **10 行表格逐条展开**，每一轮都写了「测试代码 / 跑出来看到什么 / 生产代码最少写成什么」。
> 两份文件的**方法名不一样**（本节用 `should_throw_when_...`，清单用 `should_reject_when_...`），**以详解为准**，本节只当流程参考。写完 10 条后你可以顺手把 `Username` 里 `value()` 之外的结构按本节末尾的样子收一收。
> 另外本节用的是 `@Nested` 分组写法，详解用的是扁平写法 —— **二选一，别混用**。

拿一个**零依赖**的任务项开刀：登录需求里「输入校验」拆出来的 `Username` 值对象。它能让你在一分钟内体验完整流程，不用碰数据库、不用碰 Spring。

### 5.1 先写测试（此时生产代码一行都不存在）

新建 `mall-auth/src/test/java/com/mall/auth/domain/model/UsernameTest.java`：

```java
package com.mall.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Username 值对象")
class UsernameTest {

    @Nested
    @DisplayName("创建")
    class Create {

        @Test
        @DisplayName("用户名为空白 → 抛 IllegalArgumentException")
        void should_throw_when_username_is_blank() {
            assertThatThrownBy(() -> new Username("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名");
        }

        @Test
        @DisplayName("用户名长度不足 4 位 → 抛 IllegalArgumentException")
        void should_throw_when_username_too_short() {
            assertThatThrownBy(() -> new Username("abc"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("合法用户名 → 创建成功且值原样保留")
        void should_create_when_username_is_valid() {
            assertThat(new Username("zhanghao").value()).isEqualTo("zhanghao");
        }
    }

    @Nested
    @DisplayName("值语义")
    class ValueSemantics {

        @Test
        @DisplayName("同值的 Username 应彼此相等")
        void should_be_equal_when_value_is_same() {
            assertThat(new Username("zhanghao")).isEqualTo(new Username("zhanghao"));
        }
    }
}
```

### 5.2 运行 → 得到**编译红**

```bash
/d/maven/apache-maven-4.0.0-rc-5/bin/mvn -B -pl mall-auth test
```

你会看到（这就是第一个红，正常）：

```text
[ERROR] .../UsernameTest.java:[xx,xx] 找不到符号
  符号:   类 Username
[INFO] BUILD FAILURE
```

> ⚠️ 注意：编译失败时 surefire **不会**报 `Tests run:`。看到 `BUILD FAILURE` 但没看到用例统计，说明是编译红，不是断言红。别搞混。

### 5.3 写**空壳**生产代码，让它能编译

新建 `mall-auth/src/main/java/com/mall/auth/domain/model/Username.java`：

```java
package com.mall.auth.domain.model;

/** 用户名值对象。TDD 第一步：先写空壳让测试能编译，再逐步补行为。 */
public final class Username {

    private final String value;

    public Username(String value) {
        this.value = value;          // 校验逻辑还没有 —— 故意的
    }

    public String value() {
        return value;
    }
}
```

再跑一次 → 现在是**断言红**：

```text
[ERROR]   UsernameTest$Create.should_throw_when_username_is_blank
[ERROR]   UsernameTest$Create.should_throw_when_username_too_short
[ERROR]   UsernameTest$ValueSemantics.should_be_equal_when_value_is_same
[INFO] Tests run: 4, Failures: 3
```

**这就是标准的 TDD 红。** 4 个用例里 1 个碰巧过了（合法用户名那个），3 个红。

### 5.4 写**最小实现**，把红变绿

只改 `Username`，不要动测试（测试是需求，不许为了让测试过而改测试）：

```java
package com.mall.auth.domain.model;

import java.util.Objects;

public final class Username {

    private static final int MIN_LENGTH = 4;

    private final String value;

    public Username(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (value.trim().length() < MIN_LENGTH) {
            throw new IllegalArgumentException("用户名长度不能少于 " + MIN_LENGTH + " 位");
        }
        this.value = value.trim();
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Username)) {
            return false;
        }
        return Objects.equals(value, ((Username) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "Username{" + value + "}";
    }
}
```

再跑 → **绿**：`Tests run: 4, Failures: 0, Errors: 0`

> Java 8 没有 record。想让编译器帮你生成 `equals/hashCode/getter`，可以在类上加 Lombok 的 `@Value`，  
> 但**至少第一次请手写** —— 亲手写一遍你才知道测试在驱动什么。

### 5.5 重构（测试必须保持全绿）

现在轮到你改**结构**，不改**行为**：

- 抽出常量？已经做了（`MIN_LENGTH`）。
- 觉得 `IllegalArgumentException` 不合适、想换成自定义的 `BizException`？  
  那就**先改测试** → 看红 → 再改生产代码 → 变绿。**不要在重构阶段改行为**。
- 觉得 `value()` 这个命名不好？直接改名，跑测试，绿了就继续。

每做一步重构，**都要再跑一次测试**。重构的定义是"测试全绿前提下的结构调整"。

### 5.6 提交

```bash
git add -A && git commit -m "feat(mall-auth): Username 值对象（TDD 第一个循环）"
```

---

## 6. 循环节奏：之后每个任务项照这个来

```text
① 从 .tdd/task/xx.md 里挑 1 个任务项（不要贪多）
        ↓
② 把它的「测试思路」按第 4 节的翻译表写成 @Test
        ↓
③ 跑测试 → 红（编译红或断言红，都要亲眼看一次）
        ↓
④ 写最小实现 → 跑测试 → 绿
        ↓
⑤ 重构 → 跑测试 → 仍绿
        ↓
⑥ commit → 回到 ①
```

**自查三问**（每个循环结束时问自己）：

- 我**先**看到红了吗？（没看到就说明你在写"事后测试"）
- 我的实现里有没有写测试没要求的东西？（有就删掉，那是 YAGNI）
- 我做重构的时候测试是绿的全程吗？

---

## 7. 分层测试对照表

| 层                | 测什么              | 工具 / 注解                                            | 单次耗时 | 占比   |
| ---------------- | ---------------- | -------------------------------------------------- | ---- | ---- |
| `domain`         | 规则、值对象、状态流转      | 纯 JUnit 5 + AssertJ，**不碰 Spring**                  | 毫秒   | ~70% |
| `application`    | 用例编排、分支、事件发布     | `@ExtendWith(MockitoExtension.class)` + `@Mock` 端口 | 毫秒   | ~20% |
| `infrastructure` | SQL / 映射正确性      | 内嵌 SQLite（临时文件或内存库）                                | 百毫秒  | ~8%  |
| `adapter/web`    | 请求校验、状态码、JSON 结构 | `@WebMvcTest` + `MockMvc`                          | 秒级   | ~2%  |

**关键**：不要为了图省事全用 `@SpringBootTest`。启动一次 Spring 上下文要几秒，TDD 的"快速反馈"就没  
了。**能纯 JUnit 就纯 JUnit。**

`application` 层如果一定要起 Spring，务必加：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
```

否则它会去抢 8082 端口起一个真实 Tomcat，又慢又容易和正在跑的实例冲突。

---

## 8. 领域层的 8 条设计纪律

这几条直接决定你的 TDD 顺不顺，比写测试技巧重要。

1. **domain 包不许 import Spring / MyBatis / 任何框架类。** 一旦破了，你的测试就必须起容器。
2. **值对象不可变**（`final` 字段、无 setter），相等性基于值。
3. **业务规则不是 if-else 树，而是值对象 + 显式规则对象。** 用户名格式、密码强度、账号"可登录性"这类规则各自一个类，可单独测、可组合。
4. **对外依赖用接口（端口）表达，定义在 domain 或 application，实现在 infrastructure。** 依赖方向永远是外层指向内层。
5. **时间、随机数、UUID 不许直接调。** 注入 `TimeProvider` / `TokenIssuer` 端口，否则"令牌过期""最后登录时间"这类用例你没法稳定测（也写不出不用 `sleep` 的 TTL 测试）。
6. **异常分两类**：参数非法 → `IllegalArgumentException`；业务失败 → 自定义 `BizException`（带错误码）。测试要断言的是"哪一类 + 什么提示"。
7. **每个 public 方法都应该有对应测试。** 没有被测试调用的 public 方法，先删掉。
8. **不要为了让测试变绿而放宽生产代码。** 反过来，**也绝不要为了过测试而改测试的断言** —— 除非是你发现需求理解错了（那要记录在 commit message 里）。

---

## 9. 坑清单

| # | 坑 | 现象 | 对策 |
| - | - | -- | -- |


| 1 | surefire 版本不受控 | 测试**一个都不跑**却报 `BUILD SUCCESS` | 已修（钉了 3.2.5）。保留 `TddWiringSmokeTest` 当哨兵 |  
| 2 | 编译红误当断言红 | 只有 `BUILD FAILURE`，没有 `Tests run:` | 看有没有 `找不到符号`；surefire 没统计就是编译没过 |  
| 3 | 测试连生产库 | 跑到一半把 `shop/shop.db` 改了 | 本模块还没配数据源，安全；**接 MyBatis 时用** `jdbc:sqlite:file:test-auth?mode=memory&cache=shared` 或 `@TempDir` |  
| 4 | 中文字符乱码 | 控制台断言信息变 `�����` | 只是显示问题；读 `target/surefire-reports/*.txt` |  
| 5 | 起 Spring 抢端口 | `Port 8082 was already in use` | 用 `WebEnvironment.NONE`，或 `RANDOM_PORT` |  
| 6 | 命令行启动被注入变量顶掉端口 | 明明配了 8082，却去找别的端口 | 启动前 `unset SERVER__PORT`，或 `-Dspring-boot.run.arguments=--server.port=8082` |  
| 7 | `-o` 离线模式踩雷 | `xxx.jar (present, but unavailable)` | 别加 `-o`；本项目无 parent，联网最省事 |  
| 8 | IDE 与命令行 JDK 不一致 | IDEA 绿、命令行红（或反之） | 统一：IDEA 的 Project SDK 设成 `D:\jdk17`，和命令行 `JAVA_HOME` 一致 |  
| 9 | 测试之间共享状态 | 单跑绿、全跑红 | 每个 `@Test` 自己造数据，不用 `static` 可变字段；`@BeforeEach` 里重建 |  
| 10 | 一次写太多测试 | 红一片，不知道该实现哪个 | 一次只写一个用例，让它红了再动生产代码 |

---

## 10. 什么算"这一轮做完了"（DoD）

- [ ] `.tdd/task/` 下有完整任务拆解，且通过第 3.5 节的 5 条自查
- [ ] `mvn -B -pl mall-auth test` 全绿
- [ ] `.tdd/task/` 里每个任务项在 `PROGRESS.md` 里都有打勾状态
- [ ] domain 包可以用 `grep -r "^import org.springframework" mall-auth/src/main/java/com/mall/auth/domain` 验证：**零结果**
- [ ] `git log --oneline` 能看到"一循环一提交"的痕迹（这是你 TDD 过程的证据）

---

## 附：要不要并进微服务网格？

现在 `mall-auth` 是**孤岛**（没接 Nacos / Gateway / DB），这是刻意的 —— 让 TDD 快。  
等业务逻辑跑通、你确实需要它对外提供服务时，再单独做这三件事（每件都是独立提交）：

1. 模块 `pom.xml` 加 `spring-cloud-starter-alibaba-nacos-discovery` + `nacos-config` + `spring-cloud-starter-bootstrap`
2. `bootstrap.yml` 配 Nacos 地址与 `namespace: zhanghao`（对齐现有模块，别串到同学的环境）
3. `gateway` 加路由 `/auth/**` → `lb://mall-auth`

**顺序很重要**：先让测试全绿，再去接注册中心。反过来的话，你会把"环境问题"和"逻辑问题"搅在一起排查。
