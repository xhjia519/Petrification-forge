---
kind: external_dependency
name: Forge 65.x 模组加载器（Minecraft 26.2）
slug: forge
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
source_files:
    - build.gradle
    - gradle.properties
    - src/main/resources/META-INF/mods.toml
---

### 角色与集成点
- `mods.toml` 声明 `modLoader="javafml"`，要求 Forge loaderVersion `[65,)`，并依赖 minecraft `[26.2,26.3,)`。

### 关键约束
- Java toolchain 锁定为 25（Mojang 在 26.1+ 随游戏分发 Java 25）。
- 运行配置启用 `eventbus.api.strictRuntimeChecks=true` 和 `forge.enabledGameTestNamespaces=petrification`。
- 数据生成任务 `data` 输出到 `src/generated/resources`，并从 `src/main/resources` 合并已有资源。

### 升级注意事项