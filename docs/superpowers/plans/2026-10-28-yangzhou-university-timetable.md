# 扬州大学课表 App 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 executing-plans 逐任务实现此计划。步骤使用复选框跟踪进度。

**目标：** 创建一个 Kotlin + Jetpack Compose 的扬州大学课表管理 Android MVP，拥有 HITA 风格的课表浏览体验，并通过可替换的数据源隔离 URP 接入。

**架构：** 单模块 Android 应用，Compose Navigation 负责页面，ViewModel 负责状态，Repository 负责会话、缓存和数据源。`UrpScheduleDataSource` 统一真实 URP 与演示数据的接口；首版使用 `DemoUrpDataSource` 让页面可运行，并在 UI 明示演示数据。

**技术栈：** Kotlin、Android Gradle Plugin、Jetpack Compose、Material 3、Navigation Compose、ViewModel、DataStore Preferences、JUnit。

**规格：** `docs/superpowers/specs/2026-10-28-yangzhou-university-timetable-design.md`

## 全局约束

- Android 8.0（API 26）及以上。
- 包名 `cn.yzu.timetable`。
- 主题色为扬大蓝 `#1769AA`，配紫色辅助色和浅灰蓝背景。
- 首版不实现 AI 问答、成绩、考试、选课、空教室和消息推送。
- 未配置真实扬大 URP 时，必须使用演示数据标识，不伪造真实登录成功。
- 不保存明文密码；会话仅保存必要的非敏感信息。

## 审查重点（Review Focus）

- 空账号或空密码登录时，用户应看到校验提示且不会调用数据源。
- 演示数据和真实数据必须有明确来源状态，避免用户误把示例课程当成真实课表。
- 周次切换不能产生负数周次，回到本周按钮应恢复当前周。
- 没有课程的日期/节次应保持空白网格，不抛异常。
- 退出登录后返回登录页，之前的会话状态和缓存课表不可继续展示。

### 任务 1：Android 工程脚手架与主题

**文件：**
- 创建：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`
- 创建：`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`
- 创建：`app/src/main/res/values/strings.xml`、`app/src/main/res/values/themes.xml`
- 创建：`app/src/main/java/cn/yzu/timetable/MainActivity.kt`、`app/src/main/java/cn/yzu/timetable/ui/theme/Theme.kt`
- 测试：`app/src/test/java/cn/yzu/timetable/ProjectSmokeTest.kt`

- [ ] **步骤 1：编写失败的测试**

  编写 `ProjectSmokeTest`，引用尚未创建的 `BuildInfo.appId` 并断言等于 `cn.yzu.timetable`。

- [ ] **步骤 2：运行测试验证失败**

  运行 `./gradlew test`；预期因工程文件或 `BuildInfo` 不存在而失败。

- [ ] **步骤 3：实现最小工程**

  创建可编译的单模块 Compose 工程，使用稳定的 AGP/Kotlin/Compose 版本和 compileSdk 35；`MainActivity` 设置主题并显示占位根页面；提供 `BuildInfo.appId`。

- [ ] **步骤 4：运行测试验证通过**

  运行 `./gradlew test`；预期 `ProjectSmokeTest` 通过。

### 任务 2：课表领域模型与数据源

**文件：**
- 创建：`app/src/main/java/cn/yzu/timetable/data/model/ScheduleModels.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/data/urp/UrpScheduleDataSource.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/data/urp/DemoUrpDataSource.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/data/urp/UrpConfig.kt`
- 测试：`app/src/test/java/cn/yzu/timetable/data/urp/DemoUrpDataSourceTest.kt`

- [ ] **步骤 1：编写失败的测试**

  测试演示登录返回 `demo=true` 会话；测试演示课表包含周一高等数学和至少一门跨周课程；测试空账号抛出 `IllegalArgumentException`。

- [ ] **步骤 2：运行测试验证失败**

  运行 `./gradlew test --tests '*DemoUrpDataSourceTest'`；预期因模型和数据源不存在而失败。

- [ ] **步骤 3：实现数据模型和数据源**

  定义 `Term`、`ScheduleLesson`、`UserSession`、`ScheduleSource`；`UrpScheduleDataSource` 暴露 `suspend fun login` 与 `suspend fun getSchedule`；`DemoUrpDataSource` 返回稳定的扬大示例课程，周次和节次可渲染。

- [ ] **步骤 4：运行测试验证通过**

  运行同一测试命令；预期全部通过。

### 任务 3：会话与课表 ViewModel

**文件：**
- 创建：`app/src/main/java/cn/yzu/timetable/data/SessionRepository.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/ui/AppViewModel.kt`
- 测试：`app/src/test/java/cn/yzu/timetable/ui/AppViewModelTest.kt`

- [ ] **步骤 1：编写失败的测试**

  测试空输入不会登录；演示登录后状态为已登录并带课程；切换周次会限制在 1..20；退出登录清空课程和会话。

- [ ] **步骤 2：运行测试验证失败**

  运行 `./gradlew test --tests '*AppViewModelTest'`；预期因 ViewModel 不存在而失败。

- [ ] **步骤 3：实现状态协调**

  用 `StateFlow` 暴露 `AppUiState`，集中处理登录、刷新、选周和登出；`SessionRepository` 使用内存演示实现并为 DataStore 接口留位置，不保存密码。

- [ ] **步骤 4：运行测试验证通过**

  运行同一测试命令；预期全部通过。

### 任务 4：Compose 页面与导航

**文件：**
- 创建：`app/src/main/java/cn/yzu/timetable/ui/AppRoot.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/ui/components/TimetableGrid.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/ui/screens/LoginScreen.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/ui/screens/TimetableScreen.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/ui/screens/CourseDetailScreen.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/ui/screens/AssistantScreen.kt`
- 创建：`app/src/main/java/cn/yzu/timetable/ui/screens/ProfileScreen.kt`
- 修改：`app/src/main/java/cn/yzu/timetable/MainActivity.kt`

- [ ] **步骤 1：实现登录和根导航**

  登录页提供学号、密码、登录按钮和演示数据提示；登录成功进入底部导航，未登录只能看到登录页。

- [ ] **步骤 2：实现周课表网格**

  显示学期、周次控制、周一至周日列、1-12 节行和多色课程卡片；空槽不占用点击区域，课程点击进入详情。

- [ ] **步骤 3：实现课程详情、AI 空白页、我的页**

  详情展示课程名、教师、教室、周次、节次和课程代码；AI 页展示“功能即将上线”的空状态；我的页展示扬大身份、数据源、登出和清空缓存操作。

- [ ] **步骤 4：运行静态与单元验证**

  运行 `./gradlew test` 和 `./gradlew assembleDebug`；预期构建成功并生成 `app/build/outputs/apk/debug/app-debug.apk`。

### 任务 5：URP 接入说明与交付验证

**文件：**
- 创建：`docs/urp-integration.md`
- 修改：`README.md`

- [ ] **步骤 1：记录真实接口待配置项**

  文档说明 JWT 登录、Bearer 鉴权、学生课表查询、学期参数和 DTO 映射位置，并列出接入扬大真实系统所需的 Base URL 与响应样例。

- [ ] **步骤 2：完成最终验证**

  运行 `./gradlew test assembleDebug`，检查 APK 存在；检查 `git diff --check` 无空白错误，并核对规格验收项。
