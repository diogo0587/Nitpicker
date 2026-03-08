# Nitpicker 🖼️🎬

**中文** | [English](README.md)

**Nitpicker** 不仅仅是一个媒体浏览与下载工具，它更被设计为一款**搭载端侧离线 AI 的下一代个人智能素材库**。

无论是从在线网站存取媒体，还是管理您手机里成千上万的本地照片与视频，Nitpicker 都能在**完全离线、无需断网上传、保护隐私**的前提下，自动为您进行图像识别、建立索引并分类您的全部视觉资产。

## ✨ 核心功能特性

### 🧠 端侧 AI 智能个人素材库 (新架构!)
*   **100% 离线 AI 图像打标**：深度集成了 Google 官方的 **ML Kit Image Labeling** 静态绑装模型。能够在断网状态下精准识别本地照片中的 400+ 种日常物品与场景（如：美食、室内、自然、宠物等）。
*   **SAF 分区存储无缝融合 (Storage Access Framework)**：您可以将手机里的任意本地文件夹直接设为“素材目录”。App 仅获取持久化的 Uri 读取权，**绝不复制文件、不占用一丁点多余存储空间**！
*   **无感后台扫描系统**：依托于 Android `WorkManager`，即便您退出了 App，庞大的照片库分析工作也会在后台静默完成。解析结果极速存入 `Room` (SQLite) 数据库中。
*   **动态智能首页 UI**：告别杂乱的文件堆叠！App 首页会根据您的图库特征，动态提取并展示命中数量最高的 7 大 AI 标签分类。此外还有一个包含全部提取词条的 "All Tags" 专属词云页，点击即可秒速呈现对应的本地图片。

### 🌐 在线素材浏览与缓存
*   高效浏览在线相册与媒体文件列表。
*   按需快速过滤文件类型（图片 / 视频）。
*   **批量并行缓存**：支持多选文件，利用自带组件异步极速下载网络素材到本地存储。
*   **自带下载管理器**：随时查看并行下载任务的进度与历史记录。

### 🎬 沉浸式媒体播控体验
*   **自带定制化视频播放器**：支持播放列表平滑连播、后台状态记忆（再次打开回到上次播放位置）。
*   **手势控制无缝图库引擎**：完全支持双指缩放、双击放大原图拖拽，以及顺滑的左右滑动翻页切换。

---

## 🏗️ 技术架构亮点
Nitpicker 严格遵循现代 Android 开发最佳实践（极客硬核）：
*   **100% Kotlin & Jetpack Compose**：采用声明式 UI 框架编写，辅以严格的 Compose Navigation 导航流和共享 ViewModel 作用域逃逸控制。
*   **Coroutines & StateFlow**：从数据库到底层逻辑全响应式架构，保障列表滑动不掉帧。
*   **Hilt 依赖注入**：全局接管 ViewModel、Repository 及 WorkManager 的复杂生命周期绑定。
*   **精密的 SQLite 兜底防御策略**：通过定制 `INSTR()` 精确字符串匹配取代 `LIKE`，彻底消灭了具有转义坑点的 SAF Uri (含 `%` 等 URL 编码) 所带来的“幽灵过期数据” Bug，实现了本地文件被外部删除时的智能同步自愈 (Self-healing)。

---

## 🚀 安装指南

您可以直接从 **[GitHub Releases](https://github.com/d3intran/Nitpicker/releases)** 页面下载最新的编译版本 `app-release.apk`：

1.  前往本项目的 **[Releases 页面](https://github.com/d3intran/Nitpicker/releases)**。
2.  下载最新版本的 `Nitpicker.apk` 安装包。
3.  在您的 Android 设备上，请确保已允许“安装来自未知来源的应用”（可在设置中开启）。
4.  点击下载好的 APK 文件进行安装。

---

## 🤝 参与贡献 

欢迎任何人以任何形式参与项目的迭代！

*   **寻找 Bug 或是提出新点子**：请毫不犹豫地创建一个 [Issue](https://github.com/d3intran/Nitpicker/issues)。
*   **直接与我取得联系交流**：欢迎邮件至 `d3intran@gmail.com`

**如果您想亲自下场贡献代码：**
1.  Fork 本仓库。
2.  开一个你的专属分支 (`git checkout -b feature/AwesomeFeature`)。
3.  提交你的神仙代码 (`git commit -m 'Add some feature'`)。
4.  推送到你 Fork 的远端分支 (`git push origin feature/AwesomeFeature`)。
5.  向我发起一个 Pull Request吧！

---

## 📄 许可证与免责声明

*   **开源协议**：本项目基于 [MIT 许可证](LICENSE) 开源。点击 `LICENSE` 查阅详情。
*   **免责声明**：此软件仅供开发者学习交流 Android 架构与前沿 API 的演进，服务器不存储任何真实文件信息。在线内容读取自第三方公开 API，作者对其中的展示内容均不承担任何责任。
