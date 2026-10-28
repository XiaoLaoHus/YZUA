# YZUA · 扬大课表

一个面向扬州大学学生的 Android 课表管理 App，参考 HITA 的周课表交互与 Material 3 视觉风格。当前版本先完成登录、周课表、课程详情、AI 空白页和个人页，真实 URP 接入通过独立数据源适配。

## 当前状态

- 技术栈：Kotlin、Jetpack Compose、Material 3、Navigation Compose
- 最低版本：Android 8.0（API 26）
- 默认数据：演示数据（登录后可浏览完整周课表）
- AI 问答：预留页面，尚未接入

## 本地运行

在安装 Android SDK、JDK 17+ 和 Gradle Wrapper 后运行：

```bash
./gradlew test
./gradlew assembleDebug
```

## URP 接入

真实扬大 URP 的 Base URL、登录字段与课表响应格式需要根据学校部署配置。适配边界见 [docs/urp-integration.md](docs/urp-integration.md)，数据源接口位于 `app/src/main/java/cn/yzu/timetable/data/urp/`。
