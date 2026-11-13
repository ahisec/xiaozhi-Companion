# 伴侣 Android

## 定位与状态

Android 原生小智 MCP 伴侣应用，提供按需视觉、前后台相机、直接拨号和触摸互动。
当前版本及 SDK 以 app/build.gradle 为准；历史 APK 验证见 docs/verification/。
真实手机兼容性与官方视觉权限仍需实机验证，不把 JVM 测试描述为实机通过。

## 技术与运行

Java 11 语言级别，JDK 17/21，Gradle 8.11.1，AGP 8.9.1，Android SDK 35。
Windows：.\gradlew.bat testReleaseUnitTest lintRelease assembleDebug
Linux/macOS：bash ./gradlew testReleaseUnitTest lintRelease assembleDebug
仓库检查：python scripts/check_repository.py
源码打包：python scripts/package_source.py

## 目录与约定

app/src/main/java/cn/bigfish/phone/ 是应用实现；app/src/test/ 是 JVM 测试。
app/src/main/assets/ 保存配置网页、角色图集与来源信息；docs/ 保存使用和开发说明。
保留 applicationId、Keystore 别名、配置存储名称，以兼容原有用户升级。
相机生命周期归 CompanionService / CameraController；Activity 只管理预览。
不记录密钥，不加入个人 MCP 地址、API Key、签名私钥、local.properties 或配置导出。
默认官方视觉；未经用户开启备用，不自动转发到自定义服务。
拨号工具仅报告提交系统，不推断接通；触摸工具不编造温度、力度或主动唤醒。
原图集权利独立于代码，保留 THIRD_PARTY_NOTICES.md 和资源来源 JSON。
改动功能时同步 README 和对应 docs；发布前更新验证记录并运行现有检查。
下一步重点为实机验证前后镜头、锁屏保活和 MIUI 行为，记录设备与系统版本。
