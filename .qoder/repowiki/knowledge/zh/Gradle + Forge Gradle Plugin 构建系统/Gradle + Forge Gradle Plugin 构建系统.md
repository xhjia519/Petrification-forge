---
kind: build_system
name: Gradle + Forge Gradle Plugin 构建系统
category: build_system
scope:
    - '**'
source_files:
    - build.gradle
    - gradle.properties
    - settings.gradle
    - gradle/wrapper/gradle-wrapper.properties
---

## 1. 使用的系统与工具

本项目采用 **Gradle** 作为构建系统，并通过官方插件 `net.minecraftforge.gradle`（版本区间 `[7.0.17,8)`）集成 Minecraft Forge 模组开发能力。Gradle Wrapper 锁定为 **Gradle 9.5.0**（通过 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 指定），确保所有开发者与 CI 环境使用一致版本。

Java 工具链目标为 **Java 25**（`java.toolchain.languageVersion = JavaLanguageVersion.of(25)`），由 `settings.gradle` 中的 `org.gradle.toolchains.foojay-resolver-convention` 插件自动解析并下载对应 JDK。

## 2. 关键文件

- `build.gradle`：核心构建脚本，定义项目元信息、Forge runs、依赖、编译选项。
- `gradle.properties`：启用 Gradle 缓存、并行、配置缓存、源码集合并等性能优化开关。
- `settings.gradle`：声明根项目名称 `examplemod`，引入 Foojay Toolchains Resolver。
- `gradle/wrapper/gradle-wrapper.properties`：锁定 Gradle 分发版本 9.5.0。
- `src/main/resources/META-INF/mods.toml`：Forge 模组清单（运行时元数据，非构建期）。

## 3. 架构与约定

### 3.1 项目结构
遵循标准 Forge MDK 布局：
- `src/main/java`：Java 源代码。
- `src/main/resources`：资源文件（assets、data、pack.mcmeta）。
- `src/generated/resources`：通过 data run 生成的资源（见下文）。

### 3.2 Forge Runs 任务
`build.gradle` 中通过 `minecraft.runs.register(...)` 注册了四个运行配置：
- `client`：启动客户端进行调试。
- `server`：以 `--nogui` 参数启动服务端。
- `gameTestServer`：用于 GameTest 的服务器。
- `data`：数据生成任务，工作目录为 `run-data`，参数为 `--mod petrification --all --output src/generated/resources --existing src/main/resources`，将生成的 JSON 输出到 `src/generated/resources`，同时保留手写资源。

所有 run 的工作目录统一设为 `run`（`data` 除外），并在每个 run 中设置系统属性：
- `eventbus.api.strictRuntimeChecks=true`
- `forge.enabledGameTestNamespaces=petrification`

### 3.3 仓库与依赖
- 仓库源：`minecraft.mavenizer`、`fg.forgeMaven`、`fg.minecraftLibsMaven`、`mavenCentral()`。
- 依赖：仅声明 `net.minecraftforge:forge:26.2-65.1.0` 作为 minecraft 依赖，以及 `net.minecraftforge:eventbus-validator:7.0.5` 作为 annotationProcessor。

### 3.4 版本策略
- 模组版本在 `build.gradle` 中硬编码为 `version = '1.0.0'`。
- Forge 版本在依赖声明中固定为 `26.2-65.1.0`。
- 无外部版本管理工具（如 Renovate、Dependabot），版本号变更需手动编辑 `build.gradle`。

### 3.5 编译约定
- 强制 UTF-8 编码：`options.encoding = 'UTF-8'`。
- 启用注解处理器 `eventbus-validator` 校验事件总线用法。
- 合并源码集：`net.minecraftforge.gradle.merge-source-sets=true`（允许主/测试源码集合并）。

## 4. 约定与约束

- **必须使用 Gradle Wrapper**：项目包含 `gradlew` / `gradlew.bat` 和锁定的 wrapper 版本，禁止直接使用系统安装的 Gradle。
- **Java 版本必须为 25**：由 toolchain 约束，不满足时构建失败。
- **数据生成输出路径固定**：`src/generated/resources` 被加入 sourceSets，新增数据应通过 data run 生成而非手写。
- **Run 工作目录固定**：除 `data` 外均为 `run` 目录，避免污染项目根目录。
- **无 CI/自动化发布脚本**：仓库中未发现 `.github/workflows`、Jenkinsfile、Makefile 或 Dockerfile；构建与打包完全依赖本地 Gradle 命令。
- **无多模块/子项目**：`settings.gradle` 仅声明单一根项目 `examplemod`。
- **无签名/发布任务**：未配置 `publishing`、`signing` 或上传到 CurseForge/Modrinth 的任务，产物为默认 Gradle 输出的 jar。

## 5. 典型构建流程

```bash
./gradlew build          # 编译 + 测试 + 打 jar
./gradlew :data          # 生成数据资源到 src/generated/resources
./gradlew :runClient     # 启动客户端调试
./gradlew :runServer     # 启动服务端调试
```

构建产物位于 `build/libs/`，由 Forge Gradle 插件按模组规范命名。