# YZUA v0.5 项目总结与 Opus 前端交接

交接日期：2026-10-28。目标：在已可用的校园功能基础上改进前端视觉与交互，不重构登录、URP、缓存或业务状态。

## 1. 项目与发布基线

- 名称：**YZUA**，全称 **Yangzhou University Agent**。
- 仓库：`https://github.com/XiaoLaoHus/YZUA`；本地目录 `C:/Users/Administrator/Desktop/AIWorkSpace/YZUA`，仅此目录是本项目仓库。
- 发布：`https://github.com/XiaoLaoHus/YZUA/releases/tag/v0.5`。Git tag `v0.5` 对应提交 `3c901358038f29072cd2e8a760f841fe17c3ff52`。Release 返回的 targetCommitish 可能仍显示 main；校验实际 tag 指向的提交，不能据此使用旧 main。
- 构建：`https://github.com/XiaoLaoHus/YZUA/actions/runs/40701788357`，SSO 脚本测试、JUnit、Debug APK 和 Lint 均成功；59 项 JUnit、6 项 SSO Node 测试。
- 安装包：`YZUA-v0.5.apk`，SHA-256 `c9432d6fd8efb453df0bfc1d42bf36115943aafcb05589274d57de67a386818e`；Android versionName `0.5.0`、versionCode `5`。
- package/applicationId：`cn.yzu.timetable`。不改包名，否则会变成另一个 App 且无法沿用本地数据。
- 发布 APK 来自该次 Actions 的 Debug 构建，尚未配置长期生产签名。不要在前端任务中改签名、发布或依赖系统。
- **v0.5 发布完成后追加本交接文档及保护工具，因此克隆后须 checkout 含本文档的最新 `codex/urp-timetable`，不要直接停在 v0.5 或旧 main。以此交接提交 SHA 作为 UI 改版基线。**

## 2. 当前已完成的产品

| 页面 | 当前行为 | UI 改版必须保留 |
| --- | --- | --- |
| 今日 | 启动默认页；本机日期、教学周、当天课、节次时间、导入状态、学期提醒 | 首次引导去我的，按当天星期与真实 weekNumbers 筛选、从早到晚排序；未校准/学期外/无课/未导入分别提示 |
| 课表 | 周视图、上一周/下一周/回到本周、节次时间、课程详情 | 周次筛选、课程跨节跨度、单双周、详情点击和手动刷新 |
| 成绩 | 本学期/全部学期范围，课程成绩、学分、统计与未通过原因 | 切换范围调用现有回调，缓存/加载/空/错误/需登录状态分开 |
| 考试 | 时间顺序显示课程、开始/结束、地点、备注 | 真空结果与请求失败不同，不虚构时间地点或座位号 |
| 我的 | 登录/刷新、退出保留数据、各查询更新时间、学期校准、清除数据/换账号、智能助手入口 | 登录主动发起；清除有确认；首周必须周一；智能助手留空白子页 |
| 登录 | 原生学号/密码，学校网页登录备用入口 | 启动 SsoLoginActivity 与结果接收完整保留，不能自动循环提交或保存密码 |

底栏固定顺序：**今日 / 课表 / 成绩 / 考试 / 我的**。智能不占底栏。AI 问答暂未实现，不能为了 UI 演示接入新的 AI 后端。

用户已在本次改名前反馈 SSO 修复“测试通过”；改名后的 APK 经自动化构建与元数据核对，但本轮没有 Android 设备验证安装后的全流程。非空考试记录仍缺少真实样例，仅有合成解析测试。

## 3. 技术与调用关系

这是 Android 客户端，没有自建服务端。这里的“后端”指学校 SSO/URP 的客户端接入、会话、解析、缓存和业务状态，它们全部需要保护。

- Kotlin 2.0.21 + Jetpack Compose / Material 3（Compose BOM 2024.12.01）。
- Android minSdk 26，compile/targetSdk 35，JDK 17，AGP 8.7.3。
- StateFlow + ViewModel + Kotlin Coroutines；OkHttp 4.12.0、Jsoup 1.18.3、Gson 2.11.0。
- 外观参考 HITA；实际学校接入已重写，不能重新换回 HITA 的其他学校后端。历史设计文档只是开发记录，**本交接、现有代码与已验证功能优先**。

```text
MainActivity（受保护：缓存绑定、生命周期日期更新）
  → AppViewModelFactory / AppViewModel（受保护：状态、会话、并发、刷新、缓存）
    → AppRoot → screens/components/theme（前端展示区）
登录：LoginScreen → SsoLoginActivity → 学校官方 SSO 表单
  → 教务精确回跳 → 受保护课表 API 验证 → 轮换 Cookie 内存交接
  → LoginScreen ActivityResult → AppViewModel.loginWithSsoCookie
  → UrpHttpDataSource → UrpParser → AcademicCache → AppUiState → UI
```

## 4. 文件修改边界

以下均为仓库相对路径。

### 可修改：展示和资源

- `app/src/main/java/cn/yzu/timetable/ui/screens/TodayScreen.kt`：今日、欢迎引导、提示条与课程卡片。
- `ui/screens/TimetableScreen.kt`、`ui/components/TimetableGrid.kt`：课表布局、颜色、网格、滚动与节次显示。保留 occursInWeek、日期和点击语义，不在 UI 另写业务算法。
- `ui/screens/AcademicScreens.kt`：成绩与考试展示。
- `ui/screens/ProfileScreen.kt`：个人页、日期输入、清除确认，保留已有事件调用和校验反馈。
- `ui/screens/LoginScreen.kt`：只改输入框、标题、布局和样式，认证桥接代码不可改。
- `ui/screens/CourseDetailScreen.kt`、`ui/screens/AssistantScreen.kt`：详情与空白助手展示。
- `ui/theme/**`、`ui/components/**`：主题和纯展示组件。新增组件使用现有状态/回调，不直接持有网络和会话。
- `ui/AppRoot.kt`：导航视觉、间距、安全区；既有 tab 顺序、回调、返回处理、登录路由和缓存提示保留。
- `res/drawable*`、`res/mipmap*`、`res/font`；`res/values*/strings.xml`、`colors.xml`、`dimens.xml`、`typography.xml`：图标字体文案等展示资源。不要用这些资源注入 URL、凭据、网络或缓存配置。

### 禁止修改：协议和业务

- `app/src/main/java/cn/yzu/timetable/data/**`：包括 model、SessionRepository、AcademicCache、AcademicCalendar 和整个 urp 目录。
- `SsoLoginActivity.kt`、`SsoLoginScript.kt`、`SsoLoginWaitPolicy.kt`：即使 Activity 看起来有界面，它也承担认证协议。学校网页备用模式、WebView 视口、Cookie 清理、来源校验、官方按钮、超时和 API 校验不可动。
- `ui/AppViewModel.kt`、`ui/AppViewModelFactory.kt`：**ui 包名不表示它们只是 UI**。AppUiState 定义也在 AppViewModel.kt，禁止改字段和默认值。
- `MainActivity.kt`、`BuildInfo.kt`、`AndroidManifest.xml`、`res/xml/**`、`res/values*/themes.xml`：启动、存储注入、权限和窗口配置不在这次 UI 范围。Compose 视觉主题改 `ui/theme/Theme.kt`。
- Gradle 配置与依赖、wrapper、`.github/workflows/**`、`app/src/test/**`、现有测试及保护脚本。禁止删测试或改断言使失败消失。
- 不重命名上述文件或包，不批量格式化它们，不为消除警告重写它们。

## 5. 前端状态与事件契约

`AppRoot` 订阅 `viewModel.state`；Composables 读取不可变 `AppUiState`，通过已有函数/回调提交用户意图。不要创建第二份真实业务状态或在 LaunchedEffect 中自动登录/刷新。

| 状态 | 含义 |
| --- | --- |
| `screen`、`session`、`loading`、`needsLogin` | App 内登录/主页路由、本次内存会话、查询等待和需主动登录提示 |
| `today`、`semester`、`week`、`currentWeek`、`academicYear`、`periods` | 本机日期、学期日期、所选课表周、学校返回周次/学年、真实节次时间；不可混为一个可随意覆盖的字段 |
| `lessons`、`todayLessons`、`selectedLesson` | 全课表、业务层计算的今日列表、详情选择；用已有 weekNumbers/occursInWeek，不能只按 weeks 范围判断单双周 |
| `scores`、`allScoreTerms`、`scoresLoaded`、`scoreError` | 当前范围成绩、范围选择、已加载和错误。分数是 String，可为等级/未公布，不强制转 Double |
| `exams`、`examsLoaded`、`examError` | 考试记录、已加载和错误；无考试是正常状态 |
| `scheduleUpdated`、`scoresUpdated`、`examsUpdated` | 各结果保存时间，不等同在线登录状态；日期不能伪装为刚刷新 |
| `onboarded`、`cacheError`、`semesterReminder`、`settingsError` | 首次引导、缓存错误、学期提醒与日期输入错误，必须保留可见反馈 |

必须继续使用的事件：`setAccount/setPassword`、`openLogin/closeLogin`、`loginWithSsoCookie`、`refresh`、`setAllScoreTerms`、`shiftWeek/resetWeek`、`selectLesson`、`saveSemester`、`dismissOnboarding`、`logout`、`clearLocalData`。Profile/Today 的上层 `onLogin/onProfile/onAssistant/onLesson` 等回调保持原路由。

特别注意 `LoginScreen`：保留 `rememberLauncherForActivityResult`、`RESULT_OK` 判定、`SsoCookieHandoff.take()?.let(vm::loginWithSsoCookie)`；原生登录传 `account/password`，备用登录传 `account/manual=true`。启动后清空 ViewModel 的 password，不保存密码，不把 Cookie 放进 saved state/日志/导航参数，不改调为 `vm.login()`（直接账密 URP 登录不可用）。

## 6. SSO / URP 行为说明（只理解，不改实现）

1. 学校入口 `http://ydjwxs.yzu.edu.cn/mobile/index`，登录 `/sigin` 导向 HTTPS `https://sso.yzu.edu.cn/login?service=...`。HTTP 教务地址是当前部署事实，不能在 UI 任务中盲改为 HTTPS。
2. 统一认证依赖官方 Angular 页面；App 向精确 HTTPS login 页填入本次凭据，等待 title/disabled 校验完成，点击一次官方按钮，让学校代码处理验证码和密码加密。禁止绕过 handler 直接 `form.submit()`，禁止点完就当认证成功。
3. 原生流程 60 秒等待，API 验证 25 秒，错误停止进度并保留可见学校网页；手动输入模式不限用户填写时间。正常手机视口不可缩成 1×1。
4. 精确 `/index` 或 `/mobile/index` 回跳后，读取课表接口路径 Cookie；用相同 User-Agent 请求受保护课表，只有响应 `xkxx` 数组才证明有效。必须交接 API 轮换后的 Cookie。
5. `SsoCookieHandoff` 一次性内存传递，`UrpHttpDataSource` 独立内存 CookieJar。成功、销毁及新登录时清理 WebView 存储，不记录认证材料；异常进程终止无法保证销毁回调。
6. URP 课表 GET `/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback`，含课程与 `jcsjbs` 节次时间，支持学年/周次元数据。
7. 成绩接口动态段由页面发现，不能写死；本学期 GET，全部学期按页面表单 POST 分页查询，POST 仅查询、不改学校数据。
8. 考试使用页面发现的 FullCalendar 事件接口 `/student/examinationManagement/examPlan/detail` 和 start/end 范围；现有真实账号无考试，非空事件解析仍以合成契约为主。

接口细节另见 `docs/urp-integration.md`，这是协议说明，不是要求重新实现。

## 7. 本地缓存和时间规则

- `noBackupFilesDir/academic-v1.json`，版本 1，临时文件原子替换；缓存课表、两个范围成绩、考试、学期、时间和引导，不存密码/Cookie/token。
- 启动直接恢复离线数据，不触发认证；退出保留缓存，“清除本地数据/更换账号”才清除。
- SSO 成功后隔离旧快照，避免手填学号和实际 SSO 身份不一致串号；不要去掉这步。当前 URP probe 未验证真实学号，不应把用户手填学号或占位显示名当已核实身份。
- 刷新各查询独立报错，一般失败保留该项缓存；会话过期保留旧快照并提示主动登录，不自动弹回网页登录。
- 本机日期每分钟前台更新；今日排序和教学周由现有 AcademicCalendar 计算。学校首页周次推算的结束日是 20 周建议，未经校准不能宣传为学校正式学期结束日。
- 不伪造缺失的时间，不把 unknown/null 显示成准确的学校数据，不在视觉改版中调整这些业务约定。

## 8. 改版后的验证

先从含交接文档的提交建分支，例如 `codex/ui-redesign`。将交接提交的 SHA 记为 `<handoff-sha>`，完成后运行（Windows 用 `gradlew.bat`）：

```text
node scripts/check-ui-scope.cjs <handoff-sha>
node --test scripts/test-*.cjs
./gradlew testDebugUnitTest assembleDebug lintDebug
git diff --check
git diff <handoff-sha> -- app/src/main/java/cn/yzu/timetable/data app/src/main/java/cn/yzu/timetable/SsoLoginActivity.kt app/src/main/java/cn/yzu/timetable/SsoLoginScript.kt app/src/main/java/cn/yzu/timetable/SsoLoginWaitPolicy.kt app/src/main/java/cn/yzu/timetable/ui/AppViewModel.kt app/src/main/java/cn/yzu/timetable/ui/AppViewModelFactory.kt app/src/main/java/cn/yzu/timetable/MainActivity.kt app/src/main/AndroidManifest.xml
```

最后一条应无输出。保护脚本无参以 v0.5 为基线；由于交接本身新增脚本/AGENTS/CI，Opus 改版时应传交接提交 SHA，避免把本次保护工具也误判为 UI 改动。检查包含已提交、已暂存、未暂存和未跟踪文件，重命名按删除+新增检查；路径检查只约束范围，不能证明允许目录内的代码语义安全，仍需人工 review。

Actions 对 `codex/ui-` 前缀分支（例如 `codex/ui-redesign`，以及来自该前缀分支的 PR）自动执行范围检查，对比远端受保护的 `origin/codex/urp-timetable` 交接基线。UI 工作仅推送 UI 分支，不直接推送或合并回基线分支；不修改/关闭工作流或保护工具来绕过失败。其他命名分支仍须人工运行检查。

真机验收必须包括：

1. 全新安装进入今日，首次引导能到我的；底栏五项正常，AI 在我的且为空白页。
2. 手机校园网/VPN 下原生 SSO 登录，成功自动返回 App；备用网页登录、验证码、取消和超时错误均可操作。
3. 登录后三个查询可用；关闭再开、断网再开均可查看缓存，不自动登录。
4. 课表周次切换/回到本周、单双周、当天课程排序、节次时间、课程详情、字体放大和深色模式正常。
5. 成绩范围切换、未公布/等级分数、考试无数据、网络失败/过期提示都不会显示成假数据。
6. 退出仍保留本地数据；清除有确认且确实清除；学期校准与过期提醒保留。

## 9. 可直接交给 Opus 的提示词

> 请在 YZUA 项目中只修改前端 UI。先阅读仓库 AGENTS.md 和 docs/OPUS-UI-HANDOFF.md，基于包含交接文档的最新 codex/urp-timetable 提交创建 UI 分支，记录该提交 SHA。保持 Kotlin + Jetpack Compose + Material 3，改进整体视觉、排版、图标、颜色、卡片和信息层级，兼顾深色模式与字体缩放。应用名称 YZUA，全称 Yangzhou University Agent；启动今日，底栏今日/课表/成绩/考试/我的，智能助手保留我的中的空白子页。只允许展示层和指定显示资源改动。禁止修改 data、SSO 三文件、AppViewModel/Factory、MainActivity、Manifest、Gradle、现有测试和工作流；LoginScreen 的 ActivityResult/凭据传递/单次 Cookie 交接和密码清空逻辑保留，不能直接走 vm.login()。不要新增网络请求、登录机制、缓存格式、AI 后端或假数据替换真实结果。对所有现有页面保留状态与事件回调，尤其加载/空/错误/离线/需登录/学期提醒。完成后运行 UI 范围检查（传交接 SHA）、全部现有测试、APK 与 Lint，提供改动文件清单、前后截图和回归结果；任何必须越界的改动先说明并等用户授权，不自行修改后端。

本交接和范围工具降低误改风险，不构成无法绕过的权限隔离。验收时必须同时看受保护文件 diff、现有测试及手机实测。
