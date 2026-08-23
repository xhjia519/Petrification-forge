Minecraft 26.2 Forge 模组开发文档（完整架构版）

模组概述

模组名称：石化（Petrification）

支持版本：Minecraft 26.2 + Forge 65.x.x

核心玩法：玩家通过6种药水将生物、方块、玩家转化为不同材质（石化、铁化、铜化、金化、钻石化、下界合金化）。被材质化的目标可挖掘掉落、可合成分解、可复原。

设计哲学：注册极少量的方块/物品，通过数据组件系统和动态着色器渲染实现无限的材质化组合。

\---

第一章：技术架构概览

1.1 核心设计原则

原则 说明
零实体注册 材质化后的生物/玩家不保留实体，全部转换为方块
单一方块通用 只注册1个雕像方块，通过数据组件区分材质类型和目标类型
数据驱动渲染 着色器根据数据组件传入的颜色/氧化值动态改变外观
完整闭环 材质化→挖掘→掉落→放置→分解→合成→还原，全流程可追溯

1.2 模块依赖关系

```
┌─────────────────────────────────────────────────────────────┐
│                      模组主类                              │
│              MaterializationMod                           │
└──────────┬──────────────────────────────────┬──────────────┘
           │                                  │
    ┌──────▼──────┐                   ┌───────▼───────┐
    │  注册层     │                   │  事件层       │
    │  Registry   │                   │  Handlers     │
    └──────┬──────┘                   └───────┬───────┘
           │                                  │
    ┌──────▼──────┐                   ┌───────▼───────┐
    │  方块/物品  │                   │  药水影响     │
    │  实体/效果  │                   │  挖掘/放置    │
    │  数据组件   │                   │  合成/分解    │
    └──────┬──────┘                   │  玩家登录    │
           │                          └───────┬───────┘
    ┌──────▼──────┐                          │
    │  渲染层     │◄─────────────────────────┘
    │  着色器     │
    │  模型缓存   │
    └─────────────┘
```

\---

第二章：数据模型设计

2.1 雕像数据组件（StatueComponent）

使用 Minecraft 26.2 的数据组件系统存储所有材质化信息。数据组件是类型安全、可扩展的容器，替代旧版 NBT。

数据字段分类

基础标识字段：

· targetType：目标类型（"BIOLOGICAL" / "PLAYER"）
· entityType：实体注册名（如 "minecraft:zombie" / "minecraft:player"）
· entityUUID：原始实体的唯一标识符
· materialType：材质类型（"stone", "iron", "copper", "gold", "diamond", "netherite"）

材质特有字段：

· oxidation：氧化进度（0.0 \~ 1.0，仅铜材质使用）
· isWaxed：是否打蜡（仅铜材质使用，锁定氧化）
· materializedTime：材质化时的游戏时间戳（用于计算氧化增速）

姿势/动作数据（所有生物/玩家通用）：

· yaw, pitch, headYaw：朝向和俯仰
· bodyYaw：身体朝向（玩家专用）
· isSneaking, isSprinting, isSwimming, isFlying：状态标志
· limbSwing, limbSwingAmount：肢体摆动参数
· isBaby：是否为幼年（生物专用）
· variant：变种信息（如羊的颜色、猫的种类）
· color：颜色数据（如狼的项圈颜色）

装备数据（使用 CompoundTag 存储）：

· mainHand, offHand：主副手物品
· helmet, chestplate, leggings, boots：盔甲
· extraItem：额外装备（鞘翅、盾牌等）

生物特有数据：

· health：生命值
· age：年龄（幼年/成年）
· customName：自定义名称（来自命名牌）
· isTamed：是否驯服
· ownerUUID：主人UUID（驯服生物）

玩家特有数据：

· playerName：玩家显示名
· gameProfile：完整玩家档案（含皮肤信息）
· foodLevel, saturation：饱食度和饱和度
· experienceLevel, experienceProgress, totalExperience：经验数据
· air：水下呼吸时间
· fallDistance：摔落距离
· score：分数（可选）
· advancements：已解锁进度（Map 存储）
· statistics：统计数据（Map 存储）

显示字段：

· displayName：最终显示的雕像名称（自动生成或自定义）
· hasCustomName：是否有自定义名称

2.2 数据流转路径

```
药水投掷 → 捕获原始数据 → 创建 StatueComponent
    ↓
StatueComponent 写入 BlockEntity（世界存储）
    ↓
挖掘方块 → 从 BlockEntity 读取 StatueComponent
    ↓
StatueComponent 附加到 ItemStack（数据组件）
    ↓
放置方块 → 从 ItemStack 读取 StatueComponent
    ↓
StatueComponent 写入 BlockEntity（恢复数据）
```

2.3 数据持久化策略

存储位置 用途 生命周期
BlockEntity（世界） 已放置雕像的数据 直到方块被破坏
ItemStack 数据组件 掉落物/背包中的雕像物品 直到被使用或销毁
服务器玩家数据文件 玩家被材质化时的备份 直到还原或手动清理

\---

第三章：方块系统设计

3.1 雕像方块（StatueBlock）

方块属性：

· 硬度 2.0（与石头相同）
· 挖掘工具：镐（pickaxe）
· 挖掘等级：木镐以上
· 爆炸抗性 6.0
· 不发光，非透明
· 方向性：使用 BlockState 的 FACING 属性（水平方向4个朝向 + 垂直方向2个）
· 可含水（waterlogged = true）

交互行为：

· 左键挖掘：掉落带数据组件的 StatueBlockItem
· 右键点击：打开雕像信息面板（显示材质类型、原始实体、氧化进度等）
· 手持还原粉右键：触发还原逻辑
· 手持蜜脾右键（铜材质）：触发打蜡逻辑

红石交互：（可选扩展）

· 接收到红石信号时发光或播放粒子效果
· 比较器输出信号强度表示氧化进度（0\~15）

3.2 雕像方块物品（StatueBlockItem）

物品属性：

· 最大堆叠数：16（雕像比较重）
· 创造模式分类：TAB\_BUILDING\_BLOCKS

物品提示（Tooltip）：

```
材质化僵尸雕像
材质：铜化 | 氧化进度：█████░░░░░ 50%
原始实体：僵尸
挖掘工具：镐
```

右键功能：

· 右键地面：放置方块，从数据组件恢复雕像数据
· 右键还原粉：触发还原逻辑（生成生物/玩家）

\---

第四章：药水系统设计

4.1 状态效果（MaterializationEffect）

效果属性：

· 类型：瞬时效果（药水命中瞬间触发，不持续）
· 颜色：每种材质对应一个颜色（铜色、金色、蓝色等）
· 图标：使用材质对应的锭/块图标

效果逻辑：

· 生效时调用 applyMaterialization() 方法，传入材质类型
· 不产生粒子效果（由药水本身的粒子负责）

4.2 药水类型

药水 材质类型 颜色（RGB） 效果
石化药水 stone 0x808080 变成石质雕像（去色+噪点）
铁化药水 iron 0xA0A0A0 变成铁质雕像
铜化药水 copper 0xB87333 变成铜质雕像（可氧化）
金化药水 gold 0xFFD700 变成金质雕像
钻石化药水 diamond 0x00FFFF 变成钻石雕像（带光泽）
下界合金化药水 netherite 0x454545 变成下界合金雕像（带高光）

每种药水包含三个变种：

· 普通药水（可饮用，但非玩家饮用会自己材质化）
· 喷溅药水（投掷后范围影响）
· 滞留药水（投掷后留下效果区域）

药水配方（酿造台）：

· 基础药水 + 对应材质锭/块 → 对应材质化药水
· 例：粗制药水 + 铜锭 → 铜化药水

4.3 药水影响范围

药水类型 范围（半径）
普通药水 仅直接命中的目标（实体）
喷溅药水 6.5 格半径（球形）
滞留药水 4.0 格半径（持续30秒，每秒触发一次）

影响对象优先级：

1. 玩家（最高优先级）
2. 生物（次优先级）
3. 方块（最低优先级）

\---

第五章：渲染系统设计

5.1 着色器程序（StatueShader）

着色器类型：使用 Minecraft 26.2 的 Core Shader 系统（兼容 Iris/OptiFine）

输入参数（Uniform）：

· uniform vec3 materialColor：材质颜色（从数据组件读取）
· uniform float oxidation：氧化进度（0.0 \~ 1.0，仅铜材质）
· uniform float time：游戏时间（用于动画效果）
· uniform bool isStatue：是否启用材质化渲染

颜色映射逻辑：

```glsl
// 伪代码（GLSL）
vec3 getMaterialColor() {
    if (materialType == "stone") {
        return vec3(0.5, 0.5, 0.5) + noise \\\* 0.1;  // 石质纹理
    }
    if (materialType == "iron") {
        return vec3(0.7, 0.7, 0.7);
    }
    if (materialType == "copper") {
        vec3 copperColor = vec3(0.72, 0.45, 0.20);   // 亮铜色
        vec3 oxidizedColor = vec3(0.29, 0.61, 0.48); // 青绿色
        return mix(copperColor, oxidizedColor, oxidation);
    }
    if (materialType == "gold") {
        return vec3(1.0, 0.84, 0.0);
    }
    if (materialType == "diamond") {
        return vec3(0.0, 0.8, 1.0) + specular;      // 加光泽
    }
    if (materialType == "netherite") {
        return vec3(0.27, 0.27, 0.27) + specular;    // 加高光
    }
    return vec3(1.0, 1.0, 1.0);  // 默认白色
}
```

特效增强（可选）：

· 石化材质：增加石纹噪点，去除饱和度
· 钻石/下界合金材质：添加边缘高光和镜面反射
· 铜材质：氧化时颜色平滑过渡（60秒达到满氧化）

5.2 方块实体渲染器（StatueBlockEntityRenderer）

核心职责：在雕像方块位置渲染生物模型，并应用材质化着色器

渲染流程：

```
1. 从 BlockEntity 读取 StatueComponent
2. 根据 entityType 获取对应的生物模型
3. 应用存储的姿势数据（limbSwing、sneaking等）
4. 获取材质颜色（从 component 计算）
5. 绑定材质化着色器，传入参数
6. 渲染模型（保持生物原有的动画状态）
7. 渲染名称标签（如果有自定义名称）
8. 如果是玩家雕像，额外渲染皮肤细节
```

性能优化策略：

· 模型缓存：维护 Map<String, EntityModel> 缓存，避免反复创建模型
· 实例化渲染：相同类型+材质的雕像使用实例化渲染（如果性能瓶颈）
· LOD 系统：远距离雕像切换为低多边形模型或平面纹理
· 异步加载：玩家皮肤异步加载，避免卡顿

5.3 渲染优先级

渲染类型 优先级 说明
透明层 高 铜材质半透明氧化层
实体层 中 生物模型主体
装饰层 低 名称标签、粒子效果

\---

第六章：交互系统设计

6.1 药水投掷事件（PotionImpactHandler）

处理流程：

```java
1. 检测药水影响区域（AABB）
2. 遍历影响区域内的所有实体（玩家/生物）
3. 对每个实体执行材质化逻辑：
   a. 检查免疫（创造模式/OP玩家）
   b. 捕获原始数据（姿势、装备、状态）
   c. 在实体位置生成雕像方块
   d. 将数据写入 BlockEntity 的数据组件
   e. 移除原实体（如果是玩家则踢出）
4. 遍历影响区域内的所有方块（范围较小）
5. 对每个方块执行材质化逻辑：
   a. 在世界的持久化数据中记录坐标 + 材质类型 + 氧化值
   b. 不替换方块，叠加着色层（或替换为原版材质方块）
6. 触发粒子效果和音效
```

6.2 挖掘事件（BlockBreakHandler）

处理流程：

```java
1. 检测被破坏的方块是否为雕像方块
2. 如果是：
   a. 取消默认掉落
   b. 从 BlockEntity 读取 StatueComponent
   c. 创建 StatueBlockItem
   d. 将 StatueComponent 附加到物品的数据组件
   e. 生成掉落物实体
   f. 清除 BlockEntity 数据
   g. 播放挖掘音效
3. 如果不是雕像方块但被材质化过（方块覆层）：
   a. 读取世界持久化数据
   b. 生成带材质化信息的自定义掉落物
   c. 清除材质化数据
```

6.3 放置事件（BlockPlaceHandler）

处理流程：

```java
1. 玩家手持 StatueBlockItem 右键放置
2. 从物品的数据组件读取 StatueComponent
3. 在目标位置生成雕像方块
4. 将 StatueComponent 写入 BlockEntity
5. 触发放置音效和粒子效果
6. 如果数据组件为空：生成默认雕像（石头材质+默认生物）
```

6.4 还原系统（RestorationHandler）

还原触发条件：

· 手持还原粉右键雕像方块（StatueBlock）
· 手持还原粉右键雕像物品（StatueBlockItem）

还原逻辑：

```java
// 还原方块中的雕像
1. 检测目标是否为雕像方块
2. 读取 BlockEntity 中的 StatueComponent
3. 根据 targetType 执行不同还原：
   a. BIOLOGICAL：
      - 在方块位置生成对应生物
      - 恢复姿势、装备、状态
      - 移除雕像方块
   b. PLAYER：
      - 检查玩家是否在线
      - 如果在线：传送到雕像位置，恢复所有数据
      - 如果离线：恢复玩家数据到服务器，等待玩家登录
      - 移除雕像方块
4. 播放还原特效（绿色粒子+音效）
5. 消耗1个还原粉
```

```java
// 还原物品中的雕像（背包中操作）
1. 检测目标是否为 StatueBlockItem
2. 读取物品数据组件中的 StatueComponent
3. 根据 targetType 执行还原：
   a. BIOLOGICAL：
      - 在玩家面前生成对应生物
      - 恢复姿势、装备、状态
      - 销毁雕像物品
   b. PLAYER：
      - 检查玩家是否在线
      - 如果在线：发送警告，不可还原在线玩家物品
      - 如果离线：还原玩家到服务器（生成实体）
      - 销毁雕像物品
4. 消耗1个还原粉
```

6.5 铜氧化系统（OxidationHandler）

氧化更新（服务器 Tick 事件）：

· 每 200 ticks（10秒）遍历所有已加载的铜化雕像
· 如果 !isWaxed 且 oxidation < 1.0：
· 氧化速度：oxidation += 0.001（约 2.7 小时氧化到满值）
· 可配置：Config 支持调整速度倍率
· 触发方块更新（重新渲染）
· 如果 oxidation >= 1.0：锁定，停止更新

打蜡交互（蜜脾右键）：

· 玩家手持蜜脾右键铜化雕像方块或物品
· 设置 isWaxed = true
· 锁定氧化进度
· 播放打蜡音效
· 消耗1个蜜脾
· 可重复打蜡但无额外效果

氧化显示：

· 方块：根据氧化值动态改变颜色（着色器处理）
· 物品 Tooltip：显示氧化进度条（字符画或颜色块）

\---

第七章：合成系统设计

7.1 分解配方（雕像 → 9个锭）

触发方式： 工作台（Crafting Table）中放入雕像物品

配方逻辑：

```
输入：1 个 StatueBlockItem
输出：9 个对应材质的锭/材料

材质映射：
- stone → 9 个石头
- iron → 9 个铁锭
- copper → 9 个铜锭
- gold → 9 个金锭
- diamond → 9 个钻石
- netherite → 9 个下界合金锭
```

特殊规则：

· 如果雕像带有 NBT 数据（如玩家名、装备），分解时这些数据丢失（只保留材质类型）
· 分解后不返还任何装备或经验值

7.2 合成配方（9个锭 → 雕像）

触发方式： 工作台中按 3x3 摆满同种锭

配方逻辑：

```
输入：9 个同种锭/材料
输出：1 个 StatueBlockItem

材质类型由输入决定：
- 9个铁锭 → 铁化雕像（默认实体类型为僵尸）
- 9个铜锭 → 铜化雕像
- 9个金锭 → 金化雕像
- 9个钻石 → 钻石化雕像
- 9个下界合金锭 → 下界合金化雕像
- 9个石头 → 石化雕像（但一般用石头合成无意义）
```

默认雕像类型： 如果未指定目标类型，默认生成 僵尸（zombie） 雕像

高级合成（可选扩展）：

· 雕像物品 + 对应染料 → 改变雕像颜色（不影响材质）
· 雕像物品 + 命名牌 → 改变显示名称

7.3 还原粉配方

合成配方：

```
材料：4 个红石粉 + 4 个火药 + 1 个下界之星
输出：1 个还原粉
```

替代方案（更平民）：

```
材料：4 个红石粉 + 4 个火药 + 1 个末影珍珠
输出：1 个还原粉
```

用途：

· 右键雕像方块：还原为生物/玩家
· 右键雕像物品：还原为生物/玩家（背包操作）
· 右键已材质化的方块（覆层版）：清除材质化效果

\---

第八章：玩家系统设计

8.1 玩家材质化特殊规则

触发条件：

· 生存模式玩家被药水命中
· 冒险模式玩家被药水命中（取决于配置）
· 创造模式/OP玩家：默认免疫（可配置为不免疫）

执行流程：

```
1. 验证玩家是否可被材质化（非创造/OP）
2. 备份玩家完整数据到服务器：
   - 玩家位置、装备、经验、进度、统计数据
   - 玩家属性（血量、饱食度、饱和度等）
3. 在玩家位置生成雕像方块
4. 将玩家数据写入 BlockEntity
5. 保存玩家数据文件（用于还原时恢复）
6. 踢出玩家（材质化生效）
7. 广播消息：玩家被材质化（可选）
```

8.2 玩家还原特殊逻辑

在线还原：

```
1. 检测玩家是否在线
2. 如果在线上：
   a. 传送玩家到雕像位置
   b. 恢复所有数据（装备、经验、状态）
   c. 移除雕像方块
   d. 发送还原成功消息
3. 如果不在线：
   a. 等待玩家登录时自动还原
   b. 或保留雕像直到玩家上线
```

离线还原（玩家登录时检测）：

```
1. 玩家登录服务器
2. 检查该玩家是否有材质化数据
3. 如果有：
   a. 在登录位置生成玩家实体
   b. 恢复所有数据
   c. 清除材质化数据
   d. 发送提示消息
```

8.3 数据备份与恢复

备份存储位置：

· 服务器 world/playerdata/ 目录（原版玩家数据）
· 模组专用目录 world/materialization/backup/（额外备份）

备份内容：

· 完整的玩家 NBT 数据
· 材质化时的位置坐标
· 材质化时间戳
· 雕像方块的位置

恢复策略：

· 优先使用模组备份数据（更完整）
· 如果模组备份丢失，使用原版玩家数据文件恢复
· 如果所有备份丢失，生成默认玩家（保留用户名和UUID）

\---

第九章：方块材质化（覆层系统）

9.1 方块材质化逻辑

与生物/玩家的区别：方块材质化后不替换方块，而是叠加着色层（或替换为原版材质方块）

两种实现方案：

方案 实现方式 优点 缺点
方案A：着色器覆层 在世界持久化数据中记录坐标+材质类型，渲染时叠加着色层 不破坏原方块，可还原 需要渲染劫持，技术复杂
方案B：方块替换 将方块替换为原版对应材质方块（石头→铁块、金块等） 简单直接，利用原版机制 丢失原始方块信息，还原受限

推荐方案：方案A（着色器覆层）

9.2 方块数据存储

```java
// 世界持久化数据存储（World PersistentData）
Map<BlockPos, BlockMaterializationData> materializedBlocks;

class BlockMaterializationData {
    String originalBlockId;      // 原始方块ID（用于还原）
    String materialType;         // 材质类型
    float oxidation;             // 氧化进度（仅铜）
    boolean isWaxed;             // 是否打蜡（仅铜）
    long materializedTime;       // 材质化时间
}
```

9.3 方块渲染劫持

实现方式：

· 使用 RenderBlockEvent 或自定义 BlockEntityRenderer
· 检测坐标是否有材质化数据
· 如果有：在方块纹理上叠加着色层（使用原版渲染管线）

性能优化：

· 只对可见方块进行劫持
· 使用实例化渲染（同材质方块批量处理）
· 缓存材质化数据到内存，避免频繁读取持久化数据

9.4 方块挖掘与掉落

挖掘逻辑：

```
1. 检测方块是否有材质化数据
2. 如果有：
   a. 取消默认掉落
   b. 生成一个自定义物品（MaterializedBlockItem）
   c. 将材质化数据写入物品NBT
   d. 清除世界持久化数据
3. 如果没有：走原版逻辑
```

放置逻辑：

```
1. 玩家手持 MaterializedBlockItem 右键放置
2. 从物品NBT读取材质化数据
3. 将数据写入世界持久化数据
4. 应用方块材质化渲染
```

\---

第十章：配置系统

10.1 配置文件结构

使用 Forge 的 Config API，支持服务器端和客户端配置。

主配置文件路径： config/materialization/common.toml

10.2 配置项清单

通用配置：

```toml
\\\[general]
    # 药水影响范围倍率（1.0 = 默认）
    potionRangeMultiplier = 1.0
    
    # 是否允许材质化创造模式玩家
    allowCreativeMaterialization = false
    
    # 是否允许材质化OP玩家
    allowOpMaterialization = false
    
    # 雕像方块硬度（默认2.0）
    statueBlockHardness = 2.0
```

氧化配置：

```toml
\\\[oxidation]
    # 铜氧化速度（每20tick增加的值）
    oxidationRate = 0.001
    
    # 最大氧化值（1.0 = 完全氧化）
    maxOxidation = 1.0
    
    # 是否启用氧化
    enableOxidation = true
```

合成配置：

```toml
\\\[crafting]
    # 分解雕像是否返还经验值
    returnExperienceOnDecomposition = false
    
    # 合成雕像是否消耗经验值
    consumeExperienceOnComposition = false
    
    # 合成雕像需要的经验等级
    experienceLevelRequired = 10
```

性能配置：

```toml
\\\[performance]
    # 雕像渲染最大距离（超过此距离使用LOD）
    statueRenderDistance = 64
    
    # 是否启用模型缓存
    enableModelCache = true
    
    # 模型缓存大小（最大数量）
    modelCacheSize = 100
    
    # 是否启用实例化渲染
    enableInstancedRendering = true
```

\---

第十一章：网络与多人游戏

11.1 数据同步策略

数据类型 同步方式 触发时机
BlockEntity 数据 数据包（Packet） 方块放置/挖掘/更新时
物品数据组件 随物品同步（自动） 物品在背包中移动时
世界持久化数据 随区块同步 区块加载/保存时
玩家材质化状态 登录/登出事件 玩家连接/断开时

11.2 同步数据包结构

雕像数据同步包（StatueDataSyncPacket）：

```
- BlockPos pos：方块位置
- StatueComponent data：完整雕像数据
- int syncType：同步类型（0=全量更新，1=增量更新）
```

药水投掷包（PotionThrowPacket）：

```
- UUID casterUuid：投掷者UUID
- Vec3 throwPos：投掷位置
- String potionType：药水类型
- float throwPower：投掷力度（1.0 = 默认）
```

11.3 多人游戏特殊处理

玩家材质化时的踢出机制：

· 服务器端触发，不依赖客户端
· 踢出理由显示材质化信息
· 客户端重新连接时自动触发还原检测

多人协作场景：

· 玩家A可以挖掘玩家B的雕像并带走
· 玩家A可以用还原粉还原玩家B的雕像（如果玩家B不在线）
· 如果玩家B在线，还原时会先将玩家B传送走再恢复

权限管理（可选扩展）：

· 只有雕像所有者（被材质化玩家本人）或OP可以还原
· 或任何人都可以还原（默认）
· 通过配置文件控制

\---

第十二章：性能优化策略

12.1 渲染性能优化

优化策略 实施方式 预期提升
模型缓存 缓存已加载的生物模型，避免重复构建 30% \~ 50%
LOD系统 远距离雕像使用简化模型 20% \~ 40%
实例化渲染 同类型雕像批量绘制 50% \~ 70%（大量雕像时）
异步皮肤加载 玩家皮肤异步加载，不阻塞主线程 10% \~ 20%
剔除优化 视锥体剔除+遮挡剔除 20% \~ 30%

12.2 数据存储性能优化

优化策略 实施方式
内存缓存 使用 Long2ObjectMap 缓存材质化数据，避免频繁读取持久化存储
批量更新 氧化更新使用批量处理，而非逐个更新
惰性加载 只加载可见区域的数据，不可见区域延迟加载
压缩存储 使用 CompoundTag 压缩存储，减少磁盘占用

12.3 服务器性能优化

优化策略 实施方式
异步任务 氧化更新、数据备份使用异步任务执行
定时任务合并 多个定时任务合并为一个，减少上下文切换
配置限制 通过配置文件限制最大雕像数量（防止滥用）
区块卸载清理 区块卸载时清理材质化数据缓存，释放内存

\---

第十三章：测试计划

13.1 单元测试

测试模块 测试内容
数据组件 序列化/反序列化测试，数据完整性验证
药水效果 每种药水是否能正确触发材质化
合成系统 分解/合成配方是否正确生成物品
氧化系统 氧化值是否正确增长，打蜡是否正确锁定

13.2 集成测试

测试场景 预期结果
生物材质化→挖掘→放置→还原 生物恢复原始状态（姿势、装备、状态）
玩家材质化→踢出→重新登录→还原 玩家恢复原始状态（位置、装备、经验）
铜化方块→氧化→打蜡→挖掘→放置 氧化进度正确保存和恢复
多人同时材质化 数据不冲突，各自正确存储
大数量雕像渲染（100+） 帧率保持30FPS以上

13.3 兼容性测试

测试项目 测试对象
模组兼容性 与OptiFine、Iris、钠等渲染优化模组的兼容性
实体兼容性 支持其他模组的生物（如Alex的动物）
物品兼容性 支持其他模组的装备和武器（存储到雕像数据中）
版本兼容性 在Minecraft 26.2的不同Forge版本上测试

\---

第十四章：开发路线图

阶段一：核心机制（2-3周）

· 注册雕像方块 + 方块物品
· 实现 BlockEntity 和数据组件
· 实现基础渲染（生物模型+着色器）
· 实现药水触发逻辑（对生物）

阶段二：交互系统（1-2周）

· 实现挖掘/放置逻辑
· 实现合成/分解配方
· 实现还原系统（还原粉）
· 实现铜氧化系统

阶段三：玩家支持（1-2周）

· 实现玩家材质化逻辑
· 实现玩家数据备份与恢复
· 实现多人游戏同步
· 实现登录检测机制

阶段四：完善与优化（1-2周）

· 实现配置文件
· 实现网络同步（多人游戏）
· 性能优化（模型缓存、LOD）
· 完善视觉特效（粒子、音效）

阶段五：测试与发布（1周）

· 完整测试（单元测试+集成测试）
· Bug修复
· 文档编写
· 发布到CurseForge/Modrinth

总开发周期预估：6-10周（根据开发经验调整）

\---

附录A：常用API参考（Minecraft 26.2 Forge）

A.1 注册API

```java
// 方块注册
@SubscribeEvent
public void registerBlocks(RegisterEvent event) {
    event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
        helper.register(new ResourceLocation("modid", "statue\\\_block"), new StatueBlock(...));
    });
}

// 物品注册（使用 DeferredRegister 推荐）
public static final DeferredRegister<Item> ITEMS = 
    DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

public static final RegistryObject<Item> STATUE\\\_BLOCK\\\_ITEM = 
    ITEMS.register("statue\\\_block\\\_item", () -> new StatueBlockItem(...));
```

A.2 数据组件API

```java
// 注册数据组件
public static final ComponentType<StatueComponent> STATUE\\\_DATA = 
    ComponentType.<StatueComponent>builder()
        .codec(StatueComponent.CODEC)
        .cacheable()
        .build();

// 使用数据组件
ItemStack stack = new ItemStack(ModItems.STATUE\\\_BLOCK\\\_ITEM.get());
stack.set(ModDataComponents.STATUE\\\_DATA.get(), statueComponent);
StatueComponent data = stack.get(ModDataComponents.STATUE\\\_DATA.get());
```

A.3 网络API

```java
// 注册数据包
@SubscribeEvent
public void registerPackets(RegisterPayloadHandlerEvent event) {
    event.registrar(1)
        .play(StatueDataSyncPacket.ID, StatueDataSyncPacket.CODEC, 
              StatueDataSyncPacket::handle);
}

// 发送数据包
PacketDistributor.Server.play().send(new StatueDataSyncPacket(pos, data));
```

\---

