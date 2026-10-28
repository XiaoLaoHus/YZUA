# YZUA · 扬大课表

一个面向扬州大学学生的 Android 课表管理 App，参考 HITA 的周课表交互与 Material 3 视觉风格。当前版本先完成登录、周课表、课程详情、AI 空白页和个人页，真实 URP 接入通过独立数据源适配。

## 当前状态

- 技术栈：Kotlin、Jetpack Compose、Material 3、Navigation Compose
- 最低版本：Android 8.0（API 26）
- 默认数据：演示数据（登录后可浏览完整周课表）
- AI 问答：预留页面，尚未接入

当前是可运行原型：登录使用演示数据源，尚未实现扬州大学 URP 的真实登录和课表抓取；界面也尚未完成与 HITA 的逐页对照验收。请勿输入真实教务密码。

## 仓库与持续集成

- 本地项目目录：`AIWorkSpace/YZUA/`，该目录有独立的 `.git`。
- GitHub 仓库根目录直接对应 `YZUA/` 内的项目文件，工作区的其他项目不在同步范围。
- 提交和推送前先进入 `YZUA/`，用 `git rev-parse --show-toplevel` 确认仓库根目录。
- GitHub Actions 自动运行单元测试、Debug APK 构建和 Android Lint，并保存构建报告；成功时发布 `yzua-debug-apk` 构建产物。

### 2026-10-28

将 Android 项目隔离为独立仓库；修正 Java/Kotlin 编译目标不一致、协程测试调度和工作流失败状态传递。真实 URP 对接、持久化缓存和 UI 对照验收仍待完成。

## 本地运行

安装 Android SDK（API 35）和 JDK 17 后，使用仓库内的 Gradle Wrapper：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug lintDebug
```

## URP 接入

真实扬大 URP 的 Base URL、登录字段与课表响应格式需要根据学校部署配置。适配边界见 [docs/urp-integration.md](docs/urp-integration.md)，数据源接口位于 `app/src/main/java/cn/yzu/timetable/data/urp/`。

## URP 接入进度

已加入扬州大学移动教务入口 http://ydjwxs.yzu.edu.cn/mobile/index 的 Cookie 会话数据源，课表请求采用 planCode 和 jaxStudentSchedule/callback，并支持 JSON/HTML 解析。若学校网络要求验证码或 WebVPN，应用会提示在校园网页完成认证。
