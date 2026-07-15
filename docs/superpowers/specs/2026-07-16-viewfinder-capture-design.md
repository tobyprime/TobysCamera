# TobysCamera 取景器与确认拍摄设计

## 目标

为 Fabric 客户端增加受 Exposure 启发、但独立实现的拍摄体验：方形取景器、相机 HUD、缩放与构图网格、快门动画以及确认页。成片始终是服务器指定规格的 1:1 图像；只有确认后才上传地图 tile。

本设计参考 Exposure 的职责拆分（取景状态、HUD overlay、延迟截帧、独立图片查看），不复制其代码、资源或胶片/冲洗系统。

## 范围

首期包含：

- 手持含 `tobyscamera:camera` `custom_data` 标记的物品时打开取景器；
- 固定正方形取景范围、外部遮罩、边框、可切换构图网格；
- 平滑缩放、快门动画、取消、重拍、确认上传；
- 一帧延迟的主 RenderTarget 捕获；
- 服务器签发 Token 时指定精确 `gridSize`（1–4）；
- 结果中心裁切为 1:1 并缩放至 `gridSize * 128` 像素；
- 独立预览/确认界面。

首期不包含胶片、冲洗、相机附件、滤镜、曝光/白平衡、相机架、相册或离屏世界重渲染。未来的滤镜、裁剪、旋转等后处理通过图像处理链接入。

## 客户端状态机

```text
CLOSED
  -- P + held camera --> VIEWFINDER
VIEWFINDER
  -- left click --> AWAITING_GRANT
  -- P / Esc / camera removed --> CLOSED
AWAITING_GRANT
  -- UploadGranted(gridSize) --> CAPTURING --> PREVIEW
  -- RateLimited / UploadRejected / timeout --> VIEWFINDER
PREVIEW
  -- Use photo --> UPLOADING --> VIEWFINDER
  -- Retake / Esc --> VIEWFINDER
```

玩家在 `AWAITING_GRANT`、`CAPTURING` 或 `UPLOADING` 中不能再触发快门。每 tick 检查相机仍在主手或副手，否则释放预览图像、取消本地会话并关闭取景器。

## 取景器与控制

取景器使用 HUD overlay，而非替换世界渲染：居中显示正方形开口，外部绘制半透明遮罩、四角边框和可选构图网格。HUD 内显示缩放倍率、网格模式和快捷键提示。

- `P`：进入或退出取景器；
- 左键：在取景器中触发快门；正常攻击被拦截；
- 鼠标滚轮：在安全范围内平滑缩放；
- `G`：循环无网格、三分法、中心十字；
- `Esc`：关闭当前界面；预览中丢弃结果。

取景器 HUD 和快门遮罩不出现在最终图片中。快门触发时将 HUD 标记为隐藏，至少等待一帧稳定渲染后读取主 RenderTarget，再恢复 HUD。

## 授权、捕获和上传

左键快门只发送现有的 `CaptureIntent`。服务端复核相机、执行限流、播放附近快门声，成功后返回一次性 Token 和精确 `gridSize`。

协议中的 `UploadGranted` 由：

```text
UploadGranted(token, expiresAtEpochMillis, gridSize, tileBytes)
```

取代原来的最大网格概念。客户端必须生成 `gridSize * gridSize` 个 16,384 字节 tile；服务端验证上传的 `UploadBegin` 网格恰好等于该 grant 的 `gridSize`。这使服务器能按相机/权限/未来配置决定分辨率。

`CaptureService` 在收到授权后执行一帧延迟截图，将 `NativeImage` 转为 `BufferedImage`，中心裁切到正方形，再缩放为最终精确边长。它以 `CapturedFrame` 交给预览页。预览确认后，上传控制器将它交给后处理链和地图调色板编码器，之后发送已有的分块上传包。

## 后处理扩展点

```text
CapturedFrame
  -> CenterSquareCropProcessor
  -> ResizeToServerGridProcessor
  -> ImageProcessor[] (future filter/crop/rotate processors)
  -> MapPaletteEncoder
  -> tile upload
```

处理器只接受并返回不可变图像值；首期数组固定为裁切和缩放。以后 UI 增加裁剪/滤镜时，只在预览阶段编辑处理器配置，服务端仍只接收固定 tile 数据。

## 预览页

预览页使用动态纹理显示最终方形成片，而不是保存到客户端磁盘。它提供“重拍”和“使用照片”按钮，并显示服务器下发的地图网格（例如 `2 x 2`）。关闭、重拍、上传结束和上传拒绝时必须释放动态纹理和像素缓冲。

## 错误处理与验证

- 授权超时、限流或拒绝：显示短暂提示并回到取景器；不捕获、不上传；
- Token 在截取期间到期：释放预览，显示过期提示；
- 截图失败：恢复 HUD，显示失败提示；
- 上传失败：释放预览并回到取景器；无效 Token 的服务器踢出规则不变。

测试覆盖状态转换、精确 `gridSize` 约束、中心方形裁切、缩放输出尺寸、预览确认与重拍、HUD 不污染成片、拒绝授权时没有截图或上传，以及动态纹理释放。
