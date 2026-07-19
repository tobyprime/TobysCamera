# TobysCamera

把你眼前的 Minecraft 世界拍成可保存、可分享的地图照片。

只需要**摄影师**安装 Fabric 客户端 Mod；服务器安装 Paper/Folia 插件。成片是原版 `filled_map`，没有安装 Mod 的玩家同样可以查看、持有、交易和挂到展示框中。

> 适用于 Fabric **1.21.11 / 26.1** 客户端，以及 Paper 或 Folia **1.21.11+** 服务器。

请保持客户端 Mod 与服务端插件处于同一 TobysCamera 版本；旧客户端发送不受支持的相机数据包时会收到明确的拒绝提示。

## 玩家指南

### 开始前

向管理员索要一台相机。相机可放在主手或副手；右键相机即可打开取景器。若右键被其他物品操作占用，也可以按 `P` 开关取景器。

相机的默认快门是**鼠标左键**，不是攻击键；所有按键都可在“选项 → 控制 → 托比相机”中重新绑定。

| 默认按键 | 作用 |
| --- | --- |
| 右键 / `P` | 打开或关闭取景器 |
| 鼠标左键 | 快门 |
| 鼠标右键 | 关闭取景器 |
| `-` / `=` | 缩小 / 放大 |
| `G` | 切换参考线：关闭、三分线、十字线 |
| `R` | 打开构图面板 |

### 拍一张照片

1. 手持相机并右键，进入取景器。
2. 用缩放、参考线和构图面板调整画面。构图面板可设置旋转角度、比例、缩放和参考线；支持 `1:1`、`4:3`、`3:4`、`3:2`、`2:3`、`16:9`、`9:16`，也可输入自定义比例。
3. 按快门。客户端会截取实际渲染的世界画面并打开预览。
4. 在预览中选择成片大小、是否使用 Floyd–Steinberg 抖动，再点“打印照片”；也可点“重新拍摄”。
5. 服务器确认并保存后，会发放一只**照片袋**。照片袋带有预览、拍摄者、地点和时间信息。

照片袋的使用方式：

- 在空中**长按右键约 1 秒**：拆成对应数量的原版地图。
- 对着一块空展示框右键：若附近存在大小、朝向一致的空展示框矩形，会自动铺满整张照片。
- 打掉这组展示框中的任意一块：整组会收回为一个照片袋，避免多格照片散落。

### 常见问题

**服务器重启后，旧照片会一直占内存吗？** 不会。只有带标签的地图或相片袋位于玩家主手、副手，或已加载区块的物品展示框中时，服务器才会读取照片；失活渲染器会立即释放像素。冷数据会异步从磁盘读取，并在读取完成后的下一 tick 显示，不会阻塞服务器 tick。

**为什么快门没有反应？** 请确认仍手持带 `tobyscamera:camera` 标记的相机、取景器未被构图面板遮挡，且相机有足够胶卷（或是免胶卷相机）。

**其他玩家需要安装客户端 Mod 吗？** 不需要。他们看到的是原版地图；只有拍摄者需要客户端 Mod。

---

## 服务器管理员指南

### 安装

1. 将 `tobyscamera-plugin-<version>.jar` 放进 Paper 或 Folia 服务器的 `plugins/` 目录并启动一次。
2. 将与摄影师 Minecraft 版本相符的 Fabric 客户端 JAR 交给摄影师安装。
3. 按需编辑 `plugins/TobysCamera/config.yml`，然后执行 `/tobyscamera reload`。

`/tobyscamera reload` 需要权限 `tobyscamera.reload`（默认 OP）。它会重新读取后续上传限制；已保存的照片与数据库连接保持不受影响。

### 发放相机和胶卷

TobysCamera 读取的是物品 `minecraft:custom_data` 的**根字段**，不是 Bukkit PDC。请直接写入 `tobyscamera:*` 键，**不要**把它们放进 `PublicBukkitValues`。

以下命令使用默认键名，可从控制台或拥有权限的玩家执行：

```mcfunction
# 基础相机：最多打印 4×4 地图；初始没有胶卷
/give @s minecraft:spyglass[minecraft:custom_data={"tobyscamera:camera":1b,"tobyscamera:max_grid_size":4}]

# 胶卷：把它拿在鼠标上，点击相机所在的背包格即可装入
/give @s minecraft:paper[minecraft:custom_data={"tobyscamera:film":1b}] 64

# 无限胶卷相机
/give @s minecraft:spyglass[minecraft:custom_data={"tobyscamera:camera":1b,"tobyscamera:max_grid_size":4,"tobyscamera:no_film_required":1b}]

# 一次性魔法相机：成功开始一张照片上传时消耗一台，不需要胶卷
/give @s minecraft:spyglass[minecraft:custom_data={"tobyscamera:camera":1b,"tobyscamera:max_grid_size":4,"tobyscamera:magic_photo":1b}]

```

胶卷装入时会写入相机并更新其说明。照片的胶卷消耗等于最终地图数量：`2×2` 成片消耗 4 张。若玩家背包已满，成片会掉在其脚边；若玩家在发放时离线，服务器会在其下次加入时重试。

### 物品组件（`minecraft:custom_data`）

| 键 | 值 | 用途 |
| --- | --- | --- |
| `tobyscamera:camera` | 任意值；通常 `1b` | **必需**。将物品识别为相机。 |
| `tobyscamera:film` | 任意值；通常 `1b` | 将物品识别为可装入相机的胶卷。 |
| `tobyscamera:max_grid_size` | 正整数 | 照片可打印的最大边长；缺省为服务器 `upload.max-grid-size`。普通相机还会受剩余胶卷的平方根限制。 |
| `tobyscamera:no_film_required` | 任意值；通常 `1b` | 相机不消耗胶卷。 |
| `tobyscamera:magic_photo` | 任意值；通常 `1b` | 免胶卷的一次性**照片**相机；只有服务器接受有效上传后才扣除一台。 |

`tobyscamera:film_remaining` 是插件在装填或消耗胶卷时维护的内部计数，不建议手工发放或编辑。

> 客户端当前固定识别 `tobyscamera:camera`。为保证 Fabric 客户端与服务器一致，请保持 `camera-tag-key: "tobyscamera:camera"` 的默认值。

### 配置说明

首次启动后的 `plugins/TobysCamera/config.yml` 默认如下：

```yaml
camera-tag-key: "tobyscamera:camera"
film-tag-key: "tobyscamera:film"
token-ttl-seconds: 60

rate-limit:
  per-second: 1
  per-minute: 12

upload:
  max-grid-size: 4
  max-chunks-per-second: 120
  max-active-upload-bytes: 16777216

```

| 配置项 | 作用 |
| --- | --- |
| `token-ttl-seconds` | 服务器发放的上传令牌有效期。 |
| `rate-limit` | 每位玩家开始拍摄/上传的频率限制。 |
| `upload.max-grid-size` | 照片允许的全局最大边长。 |
| `upload.max-chunks-per-second` / `max-active-upload-bytes` | 照片上传的网络速率与并发内存保护。 |

### 部署检查清单

- 摄影师安装了与服务器版本匹配的 Fabric Mod；服务器安装了插件。
- 相机的 `custom_data` 根部存在 `tobyscamera:camera`，并非嵌套在 `PublicBukkitValues`。
- 普通相机已装入足够胶卷，或带有 `tobyscamera:no_film_required` / `tobyscamera:magic_photo`。
- 用未安装 Mod 的客户端验证：收到的地图能正常查看、展示，并在服务器重启后恢复。

## 兼容性与边界

- 插件 JAR 同时支持 Paper 与 Folia；只需部署一份。
- 成片为原版地图物品，因此可由纯原版客户端使用。
- 取景器截取的是摄影师客户端实际渲染的画面；服务端不进行离屏渲染。
- 客户端按键、构图和缩放会保存在摄影师本地的 `config/tobyscamera/viewfinder.properties`。
