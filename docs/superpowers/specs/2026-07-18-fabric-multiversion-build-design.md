# Fabric 客户端多版本构建设计

## 目标

将 TobysCamera 的 Fabric 客户端从单一 Minecraft 1.21.11 构建迁移为两个可同时维护和发布的目标：Minecraft 1.21.11 与 26.1。两个目标共享绝大多数客户端实现；只有确实发生 Minecraft/Fabric API 变化的类才按版本覆盖。

本设计同时把 Mod 版本收敛为一个根配置，并使客户端发布 JAR 的名称和收集位置稳定、可预测。

## 范围

- 支持并同时构建 Fabric 1.21.11 与 Fabric 26.1 客户端 JAR。
- 保留 `common` 和 `folia` 模块；Folia 继续以其现有 1.21.11 API 目标构建。
- 所有模块从根 `mod_version` 属性取得同一 Mod 版本。
- 允许 26.1 使用 Java 25，而其余模块继续使用 Java 21。
- 不改变客户端功能、网络协议或服务端行为。
- 不支持除 1.21.11 与 26.1 外的 Fabric Minecraft 版本。

## Gradle 项目结构

`fabric` 改为源码容器项目，保留共享的 `src/main`、`src/test` 和资源。设置脚本根据根 `supported_mc_versions=1.21.11,26.1` 注册以下实际构建项目：

- `:fabric-1.21.11`，项目目录为 `fabric/versions/1.21.11`。
- `:fabric-26.1`，项目目录为 `fabric/versions/26.1`。

两个版本项目使用同一份通用 Fabric 构建逻辑。构建逻辑将版本目录中的源码和资源先同步到生成目录，再同步共享 `fabric/src` 内容；复制策略使版本目录文件优先。每个版本项目的 main、test（以及 Loom 要求的客户端源集，如适用）均从生成目录编译。

因此，`fabric/versions/<mc-version>/src/...` 中与共享文件具有相同相对路径的文件，会只替代该 Minecraft 版本中的实现。1.21.11 初始不应有覆盖源码；26.1 仅在实际 API 不兼容时添加最小覆盖层。版本目录也包含自己的 `gradle.properties`，保存 Minecraft、Fabric Loader、Fabric API 和 Java 工具链版本。

共享的 `fabric.mod.json` 用 Gradle 属性展开，写入当前构建的 Minecraft 依赖范围，而不是固定写死为 1.21.11。

## 版本与产物规则

根 `gradle.properties` 定义唯一的 `mod_version`，根项目的 `version` 由它设置。`common`、两套 Fabric 构建和 Folia 都继承这个版本，避免目前由根构建脚本隐式维护的独立 `0.1.0-SNAPSHOT` 值。

每个 Fabric 目标从 `remapJar` 生成下列发布名：

`tobyscamera-<mod_version>+mc<mc_version>.jar`

例如：`tobyscamera-0.1.0-SNAPSHOT+mc1.21.11.jar` 和 `tobyscamera-0.1.0-SNAPSHOT+mc26.1.jar`。版本任务还会将最终 JAR 收集至 `build/libs/1.21.11/` 与 `build/libs/26.1/`，避免不同版本产物互相覆盖。Folia JAR 固定命名为 `tobyscamera-folia-<mod_version>.jar`，同样由根版本属性控制，但不伪称为 Fabric 多版本产物。

## API 兼容与工具链

1.21.11 沿用现有的 Java 21、Fabric Loader 0.18.4 和对应 Fabric API。26.1 使用该版本可用的 Loader/API 组合及 Java 25。构建逻辑为每个版本 JavaExec/编译任务选择其配置的工具链，不能由根项目的 Java 21 默认值覆盖 26.1。

由于客户端包含渲染、HUD、Mixin、输入及 payload 代码，26.1 首次编译会作为兼容性清单：共享源码可以直接编译的部分保持共享；编译或 Mixin 校验明确表明有差异的类才复制到 `fabric/versions/26.1/src` 并修改。这样不会预先分叉所有客户端代码。

## 验证

根构建提供/更新聚合验证任务，使其依赖：

- `:common:test` 和 `:folia:test`；
- 两个 Fabric 版本的测试、类编译和 remapped JAR；
- 两个最终 Fabric JAR 的内容检查，确认嵌入 `common` 协议类；
- 两个 JAR 中展开后的 `fabric.mod.json`，确认 Mod 版本统一且 Minecraft 依赖分别对应 1.21.11 与 26.1。

README 的构建说明会改为指出一次根构建会产出两个客户端 JAR、所需的 Java 21/25 工具链，以及各自的收集路径。

## 不做的事

- 不为 Folia 引入 Minecraft 26.1 服务端支持。
- 不新增运行时版本检测、反射兼容层或一个同时覆盖两版的单一 JAR。
- 不在没有构建错误或运行时 Mixin 差异证据时创建 26.1 源码副本。
