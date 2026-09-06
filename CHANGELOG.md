# NatureWhisper 开发日志 / 变更记录

> 本文档面向 **项目交接 / 切换 AI 协作者**：记录已实现功能、关键文件、踩过的技术坑、当前状态与下一步。语言以中文为主，代码/类名/路径保留英文便于检索。
>
> 快速上手：`./gradlew runClient` 进单机测试；运行时配置在 `run/config/naturewhisper.json`（代码默认见 `src/main/java/com/xiaoshi/config/NatureWhisperConfig.java`）；源码分 `src/main`(公共/服务端) 与 `src/client`(仅客户端)。

## 版本与工程
- Minecraft **1.21.1** / Fabric Loader 0.19.5 / Loom（split client source set）/ **Yarn** 映射 1.21.1+build.3 / Java 21 / modid `naturewhisper`（包根 `com.xiaoshi`）。
- 目标：自然现象模拟（星空/气候地基已存在）。参考目录：`C:\Users\17305\Desktop\Reference`（LambDynamicLights-1.21、Iris×2、ComplementaryUnbound_r5.9）。

## 2026-09-06 本次会话完成

### 1. 手持光源（客户端动态光照）✅ 已验证
- 玩家（本地+远程）手持"方块默认亮度>0 的方块物品"即发光（火把14/灯笼15/萤石15…），互相可见，零网络包。配置 `handheldLighting`。
- 代码：`com.xiaoshi.light.{HandheldLightEngine,ItemLuminance,LightSource,SectionRebuildScheduler}`；mixin `HandheldLightWorldRendererMixin`（静态 `getLightmapCoordinates` RETURN 注入）、`HandheldEntityRendererMixin`（实体亮度）、`HandheldLightWorldRendererAccessor`（暴露 `scheduleChunkRender`）、`HandheldIndigoAOMixin`。
- 主要坑（已在记忆/代码注释记录）：
  - 1.21.1 无 Iris 时，chunk 平滑(AO)光照由 **fabric 自带 Indigo** 的 `AoCalculator#getLightmapCoordinates` 执行（`@Pseudo @Mixin(targets="…indigo…aocalc.AoCalculator", remap=false)`），不是原版 `AmbientOcclusionCalculator`；注入那个静态才让"平滑光照下的不透明方块"变亮。
  - vanilla 静态注入只对非 AO/flat 路径（草、实体）生效。
  - `@Inject(at=RETURN)` 里 `cir.setReturnValue` 必须 `cancellable=true`。
  - `scheduleChunkRender` 需判 `BuiltChunkStorage` 为 null（切世界/首帧）。

### 2. 运动模糊（GPU 后处理）✅ 已验证（效果 OK；白天空/星空冲突已通过给 sky 采样 clamp 修复方向）
- 用 vanilla `PostEffectProcessor` + **自定义 ResourceFactory（先查 `naturewhisper:<path>`，未命中回退原 id）** 加载自研 shader。
- 相机速度→角度/半径 uniform，方向为镜头转动反方向拖影；HUD/手持物不被糊。
- 代码：`com.xiaoshi.post.MotionBlurPass`、`mixin.client.MotionBlurGameRendererMixin`、shader 资源在 `src/client/resources/assets/naturewhisper/shaders/program/{nw_post.vsh,nw_motion_blur.fsh,nw_blit.*}`、`post/motion_blur.json`。配置 `motionBlurEnabled/Strength`，界面有强度档位。
- 注意：`PostEffectProcessor.setUniforms` **只支持单 float**；方向用角度 float 在 shader 里 cos/sin。

### 3. 景深（Depth of Field）✅ 实现（规则按你的反馈已改：30 格探测、实体纳入、非第一人称关闭；等待最终视觉确认）
- 用主帧深度：pass 声明 `"auxtargets":[{"name":"DepthSampler","id":"minecraft:main:depth"}]` 直接采样深度纹理。
- 对焦 = 准星前方 30 格内最近方块或实体（自己做的射线/AABB slab）；无目标→对焦远处、仅近景轻微糊（`NearBlur`）。
- 关键坑：**原版在画手前会 `RenderSystem.clear(GL_DEPTH_BUFFER_BIT)`**，若在画手前采样深度读到恒为 1 → 必须把 post 注入点放在 `WorldRenderer.render(...)` **之后、清深度之前**（`@Inject at=@At(value="INVOKE", target="…WorldRenderer.render(…)", shift=Shift.AFTER)`）。
- 深度→视距：near=0.05，far=`client.gameRenderer.getFarPlaneDistance()`（=viewDistance×4）。
- 代码：`com.xiaoshi.post.DepthOfFieldPass`、shader `shaders/program/nw_dof.fsh`、`post/dof.json`。配置 `depthOfFieldEnabled/Strength`，界面有强度档位。

### 4. 光线追踪（自研体素 GI + 屏幕空间，零 Iris）🔧 **Milestone 1 进行中（尚未验证达标）**
- 已建地基：`com.xiaoshi.gi.{GiGpu,VoxelWorld,RayTracedLight}`（自管 GLSL 编译/全屏/FBO、3D 纹理、客户端体素快照+上传），shader 资源 `assets/naturewhisper/shaders/gi/*`；已在 `MotionBlurGameRendererMixin` 中让 RT 先于 DoF/MotionBlur 运行。
- 配置：`rayTracingEnabled/Strength/Distance`。
- **当前已知问题（用户反馈）**：
  1. 首版整图泛白像白雾 → 因天光/环境加色过猛；
  2. 手/整体偏黑 → 逐像素整图乘了过低的 AO，且 AO 环采样朝墙里取到实心体素；
  3. 已把 shader 改为"仅向上采样轻度遮挡 + 发光块微 GI、纯加色"的低调版，**尚未经你最终确认**，下一步是复测观感再逐步加强。
- 遗留/潜在：世界坐标重建的方向/uv 翻转需校验（`uFlipY`）；RGBA16F FBO、sampler3D、direct ByteBuffer/FloatBuffer（LWJGL 不能用堆缓冲，曾导致原生崩溃）；后续里程碑：M2 体积光轴、M3 反射/AO、M4（可选、高风险）复刻 Iris gbuffer 做真实 albedo 重光。

## 其他工程约定（新 AI 请先读）
- mixin 成员前缀 `naturewhisper$`；客户端 mixin 登记在 `src/client/resources/naturewhisper.client.mixins.json` 的 `"client"` 数组（`required:true`、包 `com.xiaoshi.mixin.client`）。
- 客户端专属代码放 `src/client/java`，公共/服务端放 `src/main/java`；不做运行时 `@Environment` 判断。
- 已有后处理全屏 pass 惯例：读主帧 `minecraft:main`（color）与 `:depth`；渲染前后 `RenderSystem.disableBlend/depthTest` … `render` … `client.getFramebuffer().beginWrite(true)` + 恢复 depth/blend。
- 星空/天空自定义在 `WorldRendererMixin.renderSky` + `com.xiaoshi.sky.*`；LightmapMixin 做夜间对比度。

## 下一步建议
1. 复测 RT M1 低调版观感（无白雾/手黑、发光块旁微 GI、角落微 AO）再逐步调参（`rayTracingStrength`、shader 系数）。
2. 用户口径中"实时光影/软阴影、GI、体积光轴、反射/AO"四个效果分里程碑推进（M1 先做地基+GI/软阴影，M2/M3 后续）。
