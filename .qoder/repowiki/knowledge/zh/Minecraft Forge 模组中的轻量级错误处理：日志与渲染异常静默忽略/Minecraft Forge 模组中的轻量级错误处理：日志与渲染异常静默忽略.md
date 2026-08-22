---
kind: error_handling
name: Minecraft Forge 模组中的轻量级错误处理：日志与渲染异常静默忽略
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/com/xiong/petrification/PetrificationMod.java
    - src/main/java/com/xiong/petrification/client/StatueBlockEntityRenderer.java
    - src/main/java/com/xiong/petrification/handler/ModEventHandlers.java
    - src/main/java/com/xiong/petrification/ModConfigSpecHolder.java
---

## 1. 使用的系统/方法

该仓库是一个基于 Minecraft Forge 26.2 的客户端/服务端混合模组，代码量较小且业务逻辑集中在事件处理器中。整体没有自定义的错误类型体系、统一的异常包装或全局错误中间件。错误处理采用以下轻量手段：
- **SLF4J 日志**：通过 `com.mojang.logging.LogUtils.getLogger()` 获取 `org.slf4j.Logger`，在模组初始化时记录一条 `info` 级别日志（`PetrificationMod.java`）。这是仓库中唯一的结构化日志输出点。
- **try/catch + 静默吞异常**：仅在客户端渲染路径中出现显式异常捕获，用于防御第三方/底层渲染 API 的不稳定调用。
- **Forge 事件结果对象**：使用 `net.minecraftforge.common.util.Result`（如 `Result.ALLOW`、`Result.DENY`）和 `InteractionResult.CONSUME` 表达“允许/拒绝”等状态，而非抛出异常。
- **配置校验由 ForgeConfigSpec 承担**：所有可调参数通过 `defineInRange` / `define` 声明范围与默认值，加载时由 Forge 负责校验并回退到默认值，无需手动 try/catch。

## 2. 关键文件

| 文件 | 作用 |
|---|---|
| `src/main/java/com/xiong/petrification/PetrificationMod.java` | 模组入口，定义 `LOGGER = LogUtils.getLogger()`，仅用于初始化成功日志 |
| `src/main/java/com/xiong/petrification/client/StatueBlockEntityRenderer.java` | 唯一出现 `try { ... } catch (Exception e)` 的位置，捕获渲染过程中的任意异常并静默忽略 |
| `src/main/java/com/xiong/petrification/handler/ModEventHandlers.java` | 集中注册 Forge 事件监听器，通过 `Result` / `InteractionResult` 控制事件是否继续传播 |
| `src/main/java/com/xiong/petrification/ModConfigSpecHolder.java` | 用 `ForgeConfigSpec.Builder` 声明全部配置项及取值范围，由 Forge 在 `ModConfigEvent` 时加载 |

## 3. 架构与约定

- **无自定义异常类型**：整个 `com.xiong.petrification.*` 包下没有定义任何继承自 `RuntimeException` / `Exception` 的业务异常类，也没有常量化的错误码。
- **渲染层隔离异常**：`StatueBlockEntityRenderer.extractRenderState` 与 `submit` 两处分别包裹了 `entityRenderDispatcher.getRenderer(...).createRenderState(...)` 与 `entityRenderDispatcher.submit(...)` 的调用，捕获 `java.lang.Exception` 后仅将渲染状态置空或直接返回，不向上传播。注释明确写为 “Silently ignore rendering errors”，表明这是一种有意为之的容错策略——客户端渲染失败不应导致游戏崩溃。
- **服务端/逻辑层依赖事件返回值**：`ModEventHandlers` 中所有交互逻辑（药水泼洒、右键方块、玩家登录、方块破坏）均通过设置 `event.setResult(Result.XXX)`、`event.setUseBlock(Result.DENY)`、`event.setCancellationResult(InteractionResult.CONSUME)` 来改变事件行为，而不是抛异常中断流程。
- **配置错误交由框架**：`ModConfigSpecHolder` 将所有可配置项（药水范围倍率、是否允许创造模式/O P 玩家石化、雕像硬度、氧化速率、最大氧化值、是否启用氧化、雕像渲染距离）通过 `defineInRange` 限制取值区间；Forge 在 `ModConfigEvent` 触发时完成解析与校验，开发者无需自行 try/catch 配置文件读取。
- **日志级别单一**：目前仅有 `LOGGER.info("Petrification mod initialized")` 一条信息日志，未使用 warn/error/debug 等级，也未在任何异常路径上记录堆栈。

## 4. 约定与约束

- **客户端渲染异常必须被吞掉**：`StatueBlockEntityRenderer` 中两处渲染调用均以 `catch (Exception e)` 包裹且不打印堆栈，属于已观察到的强制约定——渲染分支不允许让异常逃逸到上层，否则会导致客户端崩溃。
- **业务逻辑不使用 throw**：除 Java/Forge 框架自身抛出的运行时异常外，业务代码中没有 `throw new XxxException(...)` 语句，错误/异常状态通过事件返回值表达。
- **配置边界由 ForgeConfigSpec 保证**：所有数值型配置都带上下界（如 `0.1, 10.0`、`0.0, 1.0`、`16, 256`），超出范围时由 Forge 自动回退默认值，开发者不在代码中做二次校验。
- **日志仅用于生命周期提示**：当前实现中日志只用于宣告模组初始化完成，未作为错误上报通道。

总体而言，该仓库对错误处理的策略是“最小化”：核心逻辑走事件返回值，客户端渲染异常静默忽略，配置校验交给 Forge，没有构建统一错误模型或全局拦截器。