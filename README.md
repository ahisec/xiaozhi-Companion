# 伴侣 · Companion

让一台安卓手机成为小智的眼睛、触摸伙伴和电话助手。

**伴侣**是一个通过小智 MCP 接入点工作的 Android 原生应用。手机负责前后摄像头、按需抓拍、视觉接口调用、触摸记录和拨打电话，M5 等小智设备负责对话。运行时不需要电脑桥接，也不需要浏览器摄像头授权或 HTTPS 证书。

当前版本：**0.1.4** · Android **8.0+** · Java · 四个独立 Tab。

本仓库包含手机应用源码。M5Stack / ESP32 固件请参考 [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32)，固件和历史电脑桥接程序不在本仓库中。

## 能做什么

| 页面 | 功能 | 可以这样使用 |
| --- | --- | --- |
| 触摸 | Deepseek Chan 动画、轻点、长按、抚摸和震动反馈 | 摸完角色，对小智说“你感觉到我摸你了吗？” |
| 看世界 | 前后镜头切换、后台相机待命、按需抓拍、视觉模型测试 | 前置看表情，后置看眼前的物品 |
| 电话 | 手动维护联系人、电话权限授权、直接呼叫 | “帮我给妈妈打电话” |
| 设置 | MCP 连接、视觉通道、图片模式、局域网页配置 | 在手机或电脑浏览器中填写配置 |

- **前后镜头可切换**：自动保存选择；切换 Tab 或进入后台后，抓拍仍使用所选镜头。
- **按需上传图片**：相机开启后在本机待命，工具调用时抓拍一张，不持续上传视频。
- **官方视觉优先**：默认仅官方通道；用户开启备用后，才允许在官方未下发权限时使用自定义接口。
- **支持 HTTP / HTTPS**：自定义视觉服务填写完整 Chat Completions 地址；手机摄像头由原生应用调用。
- **视觉能力测试**：生成随机图形与数字图片，核对回答，帮助判断接口是否真正支持图片。
- **网页配置**：手机与电脑在同一局域网时，可通过 HTTP 页面保存配置、查看状态和测试模型。

## 快速开始

1. 从本仓库的 **Releases** 获取安装包，或按下文自行构建。维护者分发的同证书 APK 可以覆盖升级；自行构建的调试包与原分发包签名不同。
2. 打开“伴侣” → **设置**，填写小智后台提供的完整 `wss://…` MCP 接入点，保存并连接。该接入点应属于小智设备当前使用的智能体。
3. 在后台确认出现下面的四个工具。若使用过电脑桥接，请先停止旧程序，避免重复连接和相机工具重名。
4. 进入 **看世界**，选择前置或后置摄像头，开启后台相机，按系统提示允许相机权限和通知。
5. 开始一轮小智对话，例如“看看我面前是什么”。电话功能还需在 **电话** 页预先授权，并保存自己的联系人。

| MCP 工具 | 用途 | 参数 |
| --- | --- | --- |
| `self.camera.take_photo` | 从当前镜头抓拍一张并识图 | `question`：图片问题 |
| `phone_camera.get_status` | 查询相机、镜头方向和连接状态 | 无 |
| `self.phone.dial` | 通过手机电话服务直接发起呼叫 | `recipient`：已保存称呼或号码 |
| `self.touch.get_recent` | 读取最近五分钟的触摸事件 | 可选 `after_id`、`limit` |

详细步骤、MIUI 后台设置、智能体角色提示词与常见限制，见 [使用指南](docs/user-guide.md)。

### 从电脑配置手机

在手机 **设置 → 开启网页配置**，把显示的完整链接复制到同一 Wi-Fi 下的电脑浏览器中。也可访问 `http://手机IP:8769/`，填写访问口令。

当前默认口令为 `peiban@520`，登录页不展示口令提示。它是公开的通用默认值，不是每台设备独立生成的密钥；HTTP 配置页仅用于可信局域网。修改此默认值的位置见 [发布说明](docs/publishing.md)。

## 设备与已知限制

开发场景为 **原版 M5Stack Cardputer（非 ADV）+ 小米 8**。手机最低要求 Android 8.0，摄像头需支持应用使用的 Camera2 输出；电话功能需要可用的系统电话服务。其他设备及系统版本需自行验证。

- 手机的后台 / 锁屏相机受 Android 与厂商电池管理限制，MIUI 建议允许通知并设置为无限制后台运行。其他应用可能抢占摄像头。
- 官方账号有视觉权益，不代表外部 MCP 会话一定收到视觉地址与令牌；免费期限和额度以官方规则为准。应用收到权限后才调用官方接口。
- 电话权限授权后会直接提交呼叫，应用不再逐次确认。系统仍可能要求选择 SIM 或解锁；工具成功不代表已经接通，通话音频由手机处理。
- 触摸只来自手机应用的触摸区，不测温、不可靠测力，也不监听其他应用的操作。触摸不会主动唤醒 M5，需要先发起对话，让模型读取工具。
- 尚未实现持续视频传输、主动让 M5 开口、外部温度传感器或手机麦克风通话桥接。

## 本地构建

准备 **JDK 17 或 21**、Android SDK Platform **35** 和 Build Tools **35.0.0**。可在 Android Studio 打开仓库根目录并同步 Gradle；也可使用命令行，设置 `ANDROID_HOME`，或在本机 `local.properties` 中填写 SDK 路径。

Windows：

```powershell
.\gradlew.bat testReleaseUnitTest lintRelease assembleDebug
```

Linux / macOS：

```bash
bash ./gradlew testReleaseUnitTest lintRelease assembleDebug
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。

`assembleRelease` 生成未签名发布包；正式签名与升级兼容说明见 [开发指南](docs/development.md)。仓库内没有发布签名私钥。

仓库提供 [GitHub Actions 构建流程](.github/workflows/android.yml)，用于运行 JVM 测试、Android Lint 和调试 APK 构建。Actions 产物是调试包，不能作为与原签名兼容的升级包。

## 目录

```text
companion-android/
├── README.md                 项目介绍与快速开始
├── AGENTS.md                 后续代码维护约定
├── app/
│   ├── build.gradle          版本、SDK 与依赖
│   └── src/
│       ├── main/
│       │   ├── java/         Android 界面、相机、服务和 MCP 实现
│       │   ├── assets/       网页配置、角色图集与来源信息
│       │   └── res/          图标、文字与备份规则
│       └── test/             JVM 测试
├── docs/                     使用、开发、发布与历史验证记录
├── scripts/                  仓库检查和源码打包工具
├── .github/workflows/        自动构建
├── gradle/wrapper/           Gradle Wrapper
├── THIRD_PARTY_NOTICES.md    依赖和角色美术的来源说明
└── Apache-2.0.txt            依赖使用的 Apache 2.0 许可文本
```

Java 包名 `cn.bigfish.phone` 与配置存储标识保留用于兼容早期安装版本，应用显示名称为“伴侣”。各类职责见 [开发指南](docs/development.md)。

## 验证状态

0.1.4 的本地记录包括 **36 项 JVM 测试通过、Android Lint 0 错误 / 1 项依赖更新提示、APK 构建与签名验证通过**。见 [APK 验证记录](docs/verification/v0.1.4.txt)。

这些结果不等于所有手机实机验证通过。前后镜头的实际方向、锁屏保活、MIUI 行为和真实官方视觉接入仍需在使用设备上确认。GitHub Actions 需要推送仓库后实际运行，本地整理阶段未验证云端执行结果。

## 分享与许可

本项目自身代码尚未指定开源许可证；维护者需明确选择后补充 `LICENSE`。现有 [Apache-2.0.txt](Apache-2.0.txt) 是依赖许可文本，不代表全部项目代码或美术都按 Apache 2.0 授权。

触摸角色 **Deepseek Chan** 来自 [Codex Pets](https://codex-pets.net/#/pets/deepseek-chan)，作者 **Dullsaw**。素材权利独立于代码，当前未核实到任意再分发或商业使用的明确许可。分享时请保留 [第三方说明](THIRD_PARTY_NOTICES.md) 和图集来源信息。

准备上传仓库、打包源码及创建 Release 的步骤见 [发布指南](docs/publishing.md)。

感谢 [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32)、[小智 MCP 示例](https://github.com/78/mcp-calculator) 和相关开源依赖。本项目为独立伴侣应用，与小智、M5Stack、小米及 DeepSeek 官方无隶属关系。
