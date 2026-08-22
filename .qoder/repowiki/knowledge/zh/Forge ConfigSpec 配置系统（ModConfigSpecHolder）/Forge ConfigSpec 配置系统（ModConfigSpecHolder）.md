---
kind: configuration_system
name: Forge ConfigSpec 配置系统（ModConfigSpecHolder）
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/java/com/xiong/petrification/ModConfigSpecHolder.java
    - src/main/java/com/xiong/petrification/PetrificationMod.java
    - src/main/java/com/xiong/petrification/block/StatueBlockEntity.java
    - src/main/java/com/xiong/petrification/client/StatueBlockEntityRenderer.java
    - src/main/java/com/xiong/petrification/handler/ModEventHandlers.java
---

## 1. 使用的系统与框架

本模组使用 **Minecraft Forge 的 `ForgeConfigSpec`** 作为配置系统，通过 `net.minecraftforge.fml.config.ModConfig.Type.COMMON` 类型注册，由 Forge 自动在 `config/` 目录下生成 `.toml` 配置文件。该方案是 Forge 官方推荐的配置方式，支持热重载、默认值、范围校验和注释。

## 2. 核心文件与职责

- `src/main/java/com/xiong/petrification/ModConfigSpecHolder.java`：唯一配置定义中心。使用静态 `ForgeConfigSpec.Builder` 集中声明所有配置项，并通过 `@Mod.EventBusSubscriber(bus = MOD) + @SubscribeEvent ModConfigEvent` 将配置值读取到类级静态字段供全局访问。
- `src/main/java/com/xiong/petrification/PetrificationMod.java`：在构造函数中调用 `context.registerConfig(ModConfig.Type.COMMON, ModConfigSpecHolder.SPEC)` 完成配置注册。
- 消费方：`StatueBlockEntity`（氧化速率/上限开关）、`StatueBlockEntityRenderer`（渲染距离）、`ModEventHandlers`（药水范围倍率）等直接读取 `ModConfigSpecHolder` 的静态字段。

## 3. 架构与约定

### 3.1 配置项分组与命名
配置键采用 `section.key` 形式分组：
- `general.*` — 通用行为：药水范围倍率 (`potionRangeMultiplier`)、是否允许创造模式/OPL 玩家石化 (`allowCreativeMaterialization` / `allowOpMaterialization`)、雕像方块硬度 (`statueBlockHardness`)。
- `oxidation.*` — 铜氧化机制：氧化速率 (`oxidationRate`)、最大氧化值 (`maxOxidation`)、总开关 (`enableOxidation`)。
- `performance.*` — 性能相关：雕像渲染距离 (`statueRenderDistance`)。

每个配置项都通过 `.comment(...)` 提供人类可读说明，便于自动生成文档。

### 3.2 类型与约束
- 数值型使用 `defineInRange(key, default, min, max)` 强制范围校验（如 `potionRangeMultiplier` 限制在 0.1~10.0，`statueBlockHardness` 限制在 0.1~100.0，`oxidationRate` 限制在 0.0~1.0）。
- 布尔型使用 `define(key, default)` 提供开关。
- 整型用于像素/区块级参数（如 `statueRenderDistance` 限制 16~256）。

### 3.3 加载时序
- 配置 Spec 在模组构造时注册。
- Forge 在 `ModConfigEvent` 触发时调用 `onLoad`，将当前生效的配置值拷贝到 `ModConfigSpecHolder` 的 `public static` 字段（如 `potionRangeMultiplier`、`enableOxidation` 等），供运行时直接读取。
- 由于字段为 `static` 且仅在事件回调中赋值，运行时修改配置后需重启或依赖 Forge 的热重载机制刷新这些字段。

### 3.4 配置作用域
仅注册了 `COMMON` 类型配置，因此配置在服务端与客户端共享；没有单独注册 CLIENT 类型配置。

## 4. 约定与约束

- **单一持有者原则**：所有配置项必须在 `ModConfigSpecHolder` 中集中声明，禁止在其他位置新建 `ForgeConfigSpec.Builder`。
- **键名规范**：必须使用 `section.key` 两段式命名，按功能域分组（`general`、`oxidation`、`performance`）。
- **每项必带注释**：每个配置项都通过 `.comment(...)` 描述用途，这是 Forge 自动生成配置模板时的依据。
- **范围校验**：数值型配置一律使用 `defineInRange` 而非裸 `define`，以在用户编辑 `.toml` 时进行边界检查。
- **运行时访问**：其他模块通过 `ModConfigSpecHolder.<field>` 静态字段读取配置，不直接持有 `ForgeConfigSpec` 引用。
- **无环境变量/外部文件**：本仓库未实现自定义环境变量解析、`.env` 文件或 JSON/YAML 配置；全部配置走 Forge 内置 TOML 机制。
- **无配置迁移/版本管理**：未发现对配置版本的显式处理逻辑，新增配置项会直接追加到生成的 TOML 中。

## 5. 已暴露的配置项一览

| 键 | 类型 | 默认值 | 范围 | 说明 |
|---|---|---|---|---|
| `general.potionRangeMultiplier` | Double | 1.0 | 0.1 ~ 10.0 | 药水效果范围倍率 |
| `general.allowCreativeMaterialization` | Boolean | false | - | 允许创造模式玩家被石化 |
| `general.allowOpMaterialization` | Boolean | false | - | 允许 OP 玩家被石化 |
| `general.statueBlockHardness` | Double | 2.0 | 0.1 ~ 100.0 | 雕像方块硬度 |
| `oxidation.enableOxidation` | Boolean | true | - | 启用铜氧化 |
| `oxidation.oxidationRate` | Double | 0.001 | 0.0 ~ 1.0 | 每 200 ticks 氧化增量 |
| `oxidation.maxOxidation` | Double | 1.0 | 0.0 ~ 1.0 | 最大氧化值 |
| `performance.statueRenderDistance` | Int | 64 | 16 ~ 256 | 雕像渲染距离（方块） |
