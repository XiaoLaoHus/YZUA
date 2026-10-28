# YZUA · 扬大课表

面向扬州大学学生的 Android 课表、成绩和考试查询工具。沿用 HITA 风格的 Kotlin + Jetpack Compose / Material 3 界面，AI 助手保留空白页。

## 当前功能

- Android 8.0（API 26）及以上。
- 学校 SSO 网页登录：从 `/sigin` 进入扬大统一认证，回到教务 `/index` 或 `/mobile/index` 后将 Cookie 交给 API 会话。App 不读取或代填统一认证密码。登录页顶部提供“返回 App”和“取消登录”；返回 App 后可在“我的 → 退出登录”清理 App 会话。
- 本学期课表：GET 查询，支持位图周次、单双周、课程详情。
- 成绩查询：本学期与全部学期切换，动态发现查询地址；全部学期按页读取，区分等级成绩、未公布、未评估与查询关闭。
- 考试安排：读取考试页和 FullCalendar 事件接口，查询当前月份前三个月到后四个月月初；查询错误和无考试分开显示。
- 各查询独立报错、可刷新；刷新失败保留本次运行的数据。退出会清理 API 会话并取消旧请求。

## 网络与验证边界

手机需要连接校园网，或使用可访问教务系统的校园 VPN。电脑已经连接 VPN，不表示手机自动具有同样的网络。

2026-10-28 已在电脑浏览器的有效 SSO 会话中核对课表、成绩与考试页面及其脚本；成绩页可显示数据，考试页显示当前无相关考试。非空考试记录仅用合成 FullCalendar 数据测试，尚无真实非空考试样例。Android WebView 的真实账号登录仍待手机或模拟器验证，不能用编译通过代替实测。

API Cookie 保存在独立的内存 CookieJar，不写入代码、日志或持久缓存。系统 WebView 在认证过程中可能使用自己的磁盘 Cookie 存储，App 会在开始、完成及销毁登录页时清理；进程异常终止无法保证销毁回调执行。课表与成绩目前仅在本次运行中保留，离线持久缓存及与 HITA 的逐页视觉验收尚未完成。

## 仓库与持续集成

- 本地 `AIWorkSpace/YZUA/` 是独立 Git 仓库，GitHub 根目录只对应此目录内容。
- 提交前用 `git rev-parse --show-toplevel` 检查边界；不提交工作区其他项目、学校账号、Cookie 或真实个人数据。
- GitHub Actions 运行单元测试、Debug APK 与 Android Lint；产物名称为 `yzua-debug-apk` 和 `yzua-build-reports`。

### 2026-10-28

隔离 YZUA 仓库并修正 CI；将直接账号密码登录重构为学校 SSO WebView → 内存 API 会话；核对课表 GET、成绩动态 GET / 分页 POST 以及考试 FullCalendar 地址；补齐成绩、考试页面及会话切换、错误处理、分页等自动化测试。

修复手机登录后停留学校网页的问题：识别 `/mobile/index` 回调；提供“返回 App”和“取消登录”按钮。随后针对登录后立即提示失效的问题，移除页面刚可见时的提前交接，统一 WebView/API User-Agent，并读取课表接口对应路径的 Cookie。只有受保护课表接口返回 `xkxx` 数组后才清理网页登录、返回 App，不再依赖桌面首页的注销链接。验证失败会留在学校网页并显示不含 Cookie、票据的诊断码。回归测试通过，手机真实流程仍待用户验证。

## 本地构建

安装 JDK 17 和 Android SDK API 35，设置 `JAVA_HOME`、`ANDROID_HOME` 后运行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

Linux / macOS 使用 `./gradlew`。接口契约与实测边界见 [URP 接入说明](docs/urp-integration.md)。
