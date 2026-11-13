# 发布指南

返回 [README](../README.md) · [开发指南](development.md)

## 上传哪一个目录

把 `companion-android` 目录的内容作为仓库根目录，GitHub 首页就能直接显示 `README.md`。可使用 GitHub Desktop 或 Git 命令行上传；仓库包含二进制 Gradle Wrapper 和 WebP 图集，不建议逐个复制代码文件。

仓库源码与 Release 安装包分开管理。源码目录没有 APK、签名私钥、个人配置和构建缓存；维护者分发的 APK 可作为 GitHub Release 附件上传。

## 发布前需要确定

1. **项目代码许可证**：当前尚未选择。确认自己的代码适用的许可后，再添加根目录 `LICENSE`。依赖的 Apache 2.0 文本不等于项目授权。
2. **角色素材权利**：保留 [第三方说明](../THIRD_PARTY_NOTICES.md) 和 [来源 JSON](../app/src/main/assets/deepseek-chan-source.json)。当前尚未核实任意再分发或商业使用的明确许可；需要时向权利人确认。
3. **网页访问口令**：`PhoneWebServer.java` 当前使用通用默认口令，隐藏登录页提示不改变其公开性。若要改为自己的分发默认值，修改该类的 `token` 后重新测试、编译；个人 MCP / API 密钥仍应只在安装后填写。HTTP 页面仅用于可信局域网。

## 本地检查与源码打包

需要 Python 3.9+，脚本仅使用标准库：

```powershell
python scripts/check_repository.py
python scripts/package_source.py
```

默认输出 `dist/companion-android-v0.1.4-source.zip`，版本从 Gradle 文件读取。也可指定位置：

```powershell
python scripts/package_source.py --output dist/companion-github-ready.zip
```

脚本按明确的源码与文档目录收集文件，生成根目录 `SHA256SUMS.txt`，检查 ZIP 内容一致性，并为 `gradlew` 保留 Unix 可执行权限。`.gitignore` 排除本机 SDK 路径、IDE 缓存、构建目录、密钥和安装包。提交前仍应查看 Git 暂存区，避免另行添加自己的运行配置。

## GitHub Actions

推送或提交 Pull Request 后，Actions 会运行单元测试、Lint、调试构建和仓库检查，并上传调试 APK 与检查报告。也可从 Actions 页面手动触发。

整理阶段只验证了本地命令，云端流程需要上传后确认。流程只读取仓库，不使用 GitHub 发布权限或签名 secrets。

## 创建 Release

确认构建与实机验证结果后，在 GitHub 的 Releases 创建版本，例如 `v0.1.4`，说明功能、适用设备和仍待验证的限制。安装包使用维护者自己的发布密钥签名，同一密钥需妥善保管，以便后续覆盖升级。

本次已有发布包名为 `companion-v0.1.4.apk`；可附上 APK 的 SHA-256 或单独校验文件。仓库源码与 APK 不是同一文件，Actions 的调试包也不是原证书签名的升级包。

历史本地 APK 构建结果见 [0.1.4 验证记录](verification/v0.1.4.txt)。其中的签名证书指纹和 APK 哈希是公开校验信息，不是私钥。

## 本次仓库整理状态（2026-11-13）

| 范围 | 状态 | 依据 |
| --- | --- | --- |
| 应用代码 | verified-current | 保留 0.1.4 应用代码，36 项本地 JVM 测试和原 APK 构建记录 |
| 文档 / 源码交付 | changed-and-verified | README、docs、打包脚本、相对链接和 ZIP 内容检查 |
| 维护约定 | changed-and-verified | 根目录 AGENTS.md 与当前目录、命令一致 |
| 手机运行效果 | pending | 未执行本次实机安装、前后镜头与 MIUI 后台验证 |
| GitHub 发布 / CI | pending | 仓库和工作流尚未上传、运行或创建 Release |
| 许可证与素材范围 | pending | 项目代码许可证待选择；素材来源保留，授权范围未完全核实 |
| 私有配置 / 工作区 | verified-current | 源码交付白名单检查；签名与个人运行数据保留在仓库外 |
| 长期记忆 / 固件 / 电脑桥接 | out-of-scope | 本次仅整理 Android 仓库；没有修改这些内容 |

本次实际验证：

- 通过仓库自己的 Gradle Wrapper 执行 `testReleaseUnitTest lintRelease assembleDebug` 成功，36 项 JVM 测试通过；Lint 保留 1 项已知依赖更新提示，0 错误。
- `app/src` 下的 29 个文件和 `app/build.gradle` 与原 0.1.4 源码包逐字节一致，整理未修改应用运行行为。
- 仓库检查与源码打包脚本通过；文档相对链接、素材哈希、ZIP 内容和 `gradlew` 的 Unix 可执行权限已核对。脚本语法按 Python 3.9 检查，实际运行环境为 Python 3.11。
- Actions YAML 的语法、触发事件与只读权限配置已在本地解析检查；尚未运行 GitHub 云端任务。
- 首次在线下载 Gradle 遇到网络超时，之后使用与 Wrapper SHA-256 一致的本地发行包缓存完成构建。首次联网下载成功与云端 CI 仍属于待验证项。

本次没有删除旧 APK、私有签名材料或其他项目，原始工作现场保留。公开的是仓库准备结果，不代表已经推送 GitHub 或完成实机验收。
