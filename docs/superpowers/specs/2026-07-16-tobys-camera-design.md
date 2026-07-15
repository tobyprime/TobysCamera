# TobysCamera 核心拍照流程设计

## 目标与范围

TobysCamera 由 Fabric 1.21.11 客户端 Mod 和 Folia 1.21.11+ 服务端插件组成。只有拍摄者需要安装 Mod；服务器将成品制作为原版 `filled_map`，因此所有原版客户端都能持有、交易、展示和查看照片。

首期只实现核心闭环：

- 用带指定 `custom_data` 标签的相机物品触发拍照；
- 服务端处理快门提示、播放附近音效并签发上传凭证；
- 客户端将预览确认后的照片切成原版地图像素切片并上传；
- 服务端创建地图画并在重启后恢复显示。

首期不实现相机参数、分辨率选择、胶卷或其他消耗品。协议及服务端校验边界会预留这些扩展点。

## 支持范围

- 客户端：Fabric 1.21.11。
- 服务端：Folia 1.21.11 为最低版本，目标是保持对后续兼容版本的支持。
- 图片网格：按实际尺寸生成最小必要网格，从 1x1 到 4x4；每个 tile 固定为 128x128 地图像素，最大一张照片 16 张地图。

## 架构

### Fabric Mod

客户端只在主手或副手物品的 `custom_data` 含约定相机键（默认 `tobyscamera:camera`）时打开拍摄界面。它渲染世界、提供预览，并在确认后完成缩放、分网格、切片和 Minecraft 地图调色板量化。

客户端上传的不是 PNG：每一个地图切片都是恰好 16,384 字节的 128x128 地图调色板索引缓冲。这样服务端不需要 PNG 解码、缩放或图片分割。

### Folia 插件

插件是协议权威：它复核相机标签、执行限流、播放快门音效、签发和校验 Token、接收固定大小的 tile 数据、创建 `filled_map`，并将地图交付给拍摄者。

插件保存每张照片的切片数据和地图 ID 的索引。启动时它为已记录的地图 ID 重新注册静态渲染器，保证地图在重启、交易及放入展示框之后仍能显示。

## 拍摄与上传协议

所有自定义载荷使用频道 `tobyscamera:main` 并带协议版本。未来相机参数或消耗品上下文可作为版本化的可选拍摄上下文字段加入；首期服务端不依赖它们。

```text
C2S CaptureIntent
  -> S2C UploadGranted(token, expiresAt, maxGridSize=4, tileBytes=16384)
  -> S2C RateLimited(retryAfterMs)

C2S UploadBegin(token, gridWidth, gridHeight)
C2S UploadTileChunk(token, tileX, tileY, offset, bytes)
C2S UploadFinish(token)
  -> S2C PhotoCreated(photoId, mapIds, gridWidth, gridHeight)
  -> S2C UploadRejected(reason)
```

1. 客户端发送 `CaptureIntent`，仅表示按下快门。
2. 插件复核玩家当前手持的相机标签，检查每秒和每分钟限流。通过时向附近玩家播放快门音效，并签发短期有效（默认 60 秒）、绑定玩家且只能使用一次的 Token；超限时只返回 `RateLimited`，不播放音效也不签发 Token。
3. 客户端让用户预览结果。确认后发送 `UploadBegin` 和按 tile/偏移顺序分块的 `UploadTileChunk`，最后发送 `UploadFinish`。
4. 服务器校验网格在 1x1 至 4x4、tile 坐标不重复且不越界，并要求每个 tile 恰好重组为 16,384 字节。
5. 验证完成后，服务器创建地图、落盘像素数据和索引，并将地图交付给玩家。

## Token 与防滥用

Token 是一次性上传凭证，绑定签发的玩家、拍摄时确认的相机、过期时间与上传会话状态。上传处理入口首先校验 Token，只有有效 Token 才会进入字节累计和临时文件写入。

无效、过期、已使用、属于其他玩家，或不存在上传会话的 Token，出现在 `UploadBegin`、`UploadTileChunk` 或 `UploadFinish` 中时，插件立即踢出该玩家，并使用可配置提示。网络带宽已到达服务器后无法回收，但无效包不写磁盘、不拼接、不解码。

合法 Token 下的非恶意失败（格式错误、超时或网络中断）会取消该上传会话并发送失败原因，不踢出玩家。每位玩家同一时刻只能存在一个上传会话；每个包另有固定最大分块大小和接收载荷上限。

## 地图生成、交付与持久化

客户端提交的 tile 直接作为照片持久化数据，不保存原始 PNG。最大 4x4 照片最多保存 262,144 字节原始地图像素数据。

- 照片 tile 保存于 `plugins/TobysCamera/photos/<photo-uuid>/`。
- SQLite 保存照片 UUID、网格宽高、创建者、创建时间、每个 tile 坐标及其对应地图 ID。
- 每个 `filled_map` 写入 `custom_data`，其中含照片 UUID、tile 坐标及网格大小，供识别与未来补发。
- 每个地图绑定只读静态 `MapRenderer`；渲染只读取已经加载的 tile 数据，不执行磁盘 I/O。
- 服务器启动时按 SQLite 索引恢复 renderer；临时上传目录中的未完成数据会被清理。
- 成功后在玩家所属 Folia 调度器中逐张交付：优先背包，满时掉落在脚下；若玩家在交付时断线，保存待交付记录，下次登录时交付。

所有文件和 SQLite 索引的最终提交使用临时目录加原子移动的方式，避免崩溃留下可见的半成品照片。

## 配置

```yaml
camera-tag-key: "tobyscamera:camera"
token-ttl-seconds: 60
rate-limit:
  per-second: 1
  per-minute: 12
upload:
  max-grid-size: 4
  chunk-bytes: 8192
  timeout-seconds: 30
invalid-token:
  kick-message: "无效或过期的照片上传凭证"
```

限流状态仅驻留内存；服务器重启后清空。首期不踢出或临时封禁限流超额的玩家，只拒绝签发 Token 并提示可重试时间。

## Folia 线程模型

字节接收后的文件操作、索引写入和切片缓存准备在异步执行器中进行。涉及玩家背包、掉落物、地图对象及玩家消息的操作，调度回对应玩家或实体的 Folia 调度器。跨区域线程不得访问 Bukkit 实体或世界状态。

## 验证策略

- Token 签发、到期、一次性消费、玩家绑定与限流测试。
- 无效 Token 立即踢出，且不创建临时数据的测试。
- 1x1、2x2 和 4x4 的分块重组、偏移、重复 tile、越界坐标和固定 16,384 字节长度测试。
- 照片索引和 tile 数据的原子提交、启动恢复与临时目录清理测试。
- 地图交付、背包满掉落、断线待交付和再次登录交付测试。
- Folia 调度边界测试，确保异步任务不直接调用实体和世界 API。
