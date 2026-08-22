---
kind: logging_system
name: 基于 SLF4J + LogUtils 的极简日志输出
category: logging_system
scope:
    - '**'
source_files:
    - src/main/java/com/xiong/petrification/PetrificationMod.java
---

## 1. 使用的系统/方案

该模组采用 Minecraft Forge 内置的日志体系：通过 `com.mojang.logging.LogUtils` 获取 `org.slf4j.Logger` 实例，并以 SLF4J 作为日志门面。这是 Forge 官方推荐的日志方式，底层由 Mojang 的 logging 桥接实现输出到控制台与游戏日志。

## 2. 关键文件

- `src/main/java/com/xiong/petrification/PetrificationMod.java`：唯一引入并使用日志的文件。定义模块级静态 Logger 并仅调用一次 `LOGGER.info("Petrification mod initialized")` 用于模组初始化确认。

其余业务类（block、item、handler、network、registry 等）均未使用任何日志输出。

## 3. 架构与约定

- **Logger 获取方式**：在类中声明 `private static final Logger LOGGER = LogUtils.getLogger();`，遵循 SLF4J 标准模式。
- **日志级别**：当前仅使用了 `info` 级别；未发现 `debug`、`warn`、`error`、`trace` 的使用。
- **结构化字段**：未使用任何结构化日志字段（如 JSON 包裹、MDC、键值对），所有日志均为纯字符串模板。
- **集中式入口**：仅在模组主类的 `commonSetup` 钩子中输出一条初始化信息，属于一次性启动日志。
- **无自定义日志配置**：仓库中不存在 logback.xml、log4j2.xml、log4j.properties 等配置文件，也未见通过代码动态配置日志级别或追加器。

## 4. 约定与约束

- **约定**：若未来需要添加日志，应沿用 `LogUtils.getLogger()` 获取 SLF4J Logger 的方式（当前唯一实践）。
- **约束**：目前没有任何强制性的日志规范（如必须记录某类事件、禁止使用 `System.out.println` 等），因此实际约束较弱——整个项目仅有单条 info 日志。
- **范围**：该日志系统仅覆盖服务端/客户端通用初始化阶段，未在服务端事件处理、网络消息、数据加载等路径中产生日志输出。
