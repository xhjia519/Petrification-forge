---
kind: dependency_management
name: 基于 Gradle + Forge Gradle Plugin 的依赖管理
category: dependency_management
scope:
    - '**'
source_files:
    - build.gradle
    - settings.gradle
    - gradle.properties
    - gradle/wrapper/gradle-wrapper.properties
    - src/main/resources/META-INF/mods.toml
---

## 1. 使用的系统与工具

本项目采用 **Gradle** 作为构建与依赖管理系统，配合 **Forge Gradle Plugin (net.minecraftforge.gradle)**（版本范围 `[7.0.17,8)`）来管理 Minecraft/Forge 模组依赖。Java 工具链锁定为 Java 25（与 Mojang 在 Forge 26.1+ 中分发的版本一致）。Gradle Wrapper 固定使用 Gradle **9.5.0**（通过 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 锁定），确保所有开发者与 CI 环境使用相同版本的 Gradle。

## 2. 关键文件

- `build.gradle`：声明插件、仓库源、Minecraft/Forge 依赖及编译选项。
- `settings.gradle`：启用 `org.gradle.toolchains.foojay-resolver-convention` 插件以自动解析 Java Toolchain。
- `gradle.properties`：启用 Gradle 缓存、并行构建、配置缓存等性能优化项。
- `gradle/wrapper/gradle-wrapper.properties`：锁定 Gradle Wrapper 版本。
- `src/main/resources/META-INF/mods.toml`：运行时声明对 `forge` 与 `minecraft` 两个模组的强制依赖关系。

## 3. 架构与约定

### 仓库源
`repositories` 块按以下顺序声明依赖来源：
1. `minecraft.mavenizer(it)` — 由 Forge Gradle 注入的 Mojang 官方 Maven 仓库（用于获取 Minecraft 本体及其映射）。
2. `fg.forgeMaven` — Forge 官方 Maven 仓库（用于获取 Forge 工件）。
3. `fg.minecraftLibsMaven` — Minecraft 库专用 Maven 仓库。
4. `mavenCentral()` — 通用公共 Maven Central。

该顺序体现了“优先从 Mojang/Forge 官方源拉取，再回退到 Maven Central”的策略，避免第三方镜像污染核心依赖。

### 依赖声明
项目仅声明两类依赖：
- **运行时依赖**：`implementation minecraft.dependency('net.minecraftforge:forge:26.2-65.1.0')`，通过 Forge Gradle 提供的 `minecraft.dependency()` DSL 获取与当前 Minecraft 版本绑定的 Forge 工件。
- **编译期注解处理器**：`annotationProcessor 'net.minecraftforge:eventbus-validator:7.0.5'`，用于在编译期校验事件总线用法。

没有引入任何第三方业务库（如 Apache Commons、Guava 等），因此依赖面非常小。

### 运行时依赖约束
`mods.toml` 中通过 `[[dependencies.petrification]]` 条目声明：
- `modId="forge"`，`versionRange="[65,)"`，`mandatory=true`。
- `modId="minecraft"`，`versionRange="[26.2,26.3,)"`，`mandatory=true`。
这确保了游戏加载器会拒绝不兼容的 Forge/Minecraft 版本组合。

### 版本锁定策略
- **Gradle 版本**：通过 Wrapper 的 `distributionUrl` 硬编码为 `gradle-9.5.0-bin.zip`，并开启 `validateDistributionUrl=true`。
- **Forge 版本**：在 `build.gradle` 中以精确坐标 `net.minecraftforge:forge:26.2-65.1.0` 锁定。
- **Minecraft 版本**：通过 Forge 工件间接锁定（`26.2` 对应 Minecraft 1.21.x 系列），并在 `mods.toml` 中以 `[26.2,26.3,)` 范围限定。
- **Java 版本**：通过 `java.toolchain.languageVersion = JavaLanguageVersion.of(25)` 锁定。

### 无私有仓库 / 无 vendoring
项目中未发现私有 Maven 仓库配置、未检出第三方源码（vendor）、也未使用 `libs/` 目录放置本地 jar。所有依赖均通过远程 Maven 仓库解析。

## 4. 约定与约束

- **必须使用 Gradle Wrapper**：根目录提供 `gradlew` / `gradlew.bat`，且 `gradle-wrapper.jar` 已提交，禁止直接调用系统 Gradle。
- **Java 工具链由 Foojay Resolver 自动解析**：`settings.gradle` 启用的 `foojay-resolver-convention` 会在首次构建时下载指定版本的 JDK，无需开发者手动安装。
- **依赖变更需同步更新两处版本**：Forge 版本同时出现在 `build.gradle` 的 `minecraft.dependency(...)` 和 `mods.toml` 的 `loaderVersion` / `minecraft` 依赖范围中；若修改 Forge 主版本号，两者需保持一致。
- **运行时依赖不可绕过**：`mods.toml` 中 `mandatory=true` 表示 Forge 加载器会强制校验依赖，缺失或不匹配时将阻止模组加载。
- **构建性能默认开启**：`gradle.properties` 中启用了 `org.gradle.caching`、`org.gradle.parallel`、`org.gradle.configureondemand` 以及 Gradle 8+ 的配置缓存（`configuration-cache`），这是项目的默认构建约定。
- **UTF-8 编码强制**：`JavaCompile` 任务统一设置 `options.encoding = 'UTF-8'`，避免跨平台字符集差异导致的依赖资源读取问题。