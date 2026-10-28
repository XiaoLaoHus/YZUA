# YZUA 开发边界

先阅读 `docs/OPUS-UI-HANDOFF.md`。应用已可用，v0.5 为前端改版的功能基线。

## 仅修改 UI 的任务

- 允许修改 Compose `ui/screens/`、`ui/components/`、`ui/theme/`、`ui/AppRoot.kt` 的展示，以及颜色、字体、图标、间距和文案。
- 保留已有函数签名、状态字段、事件回调、登录结果接收和 Cookie 交接；不在 Composable 中发网络请求、自动登录或改缓存。
- **禁止修改** `data/**`、`SsoLoginActivity.kt`、`SsoLoginScript.kt`、`SsoLoginWaitPolicy.kt`、`ui/AppViewModel.kt`、`ui/AppViewModelFactory.kt`、`MainActivity.kt`、`BuildInfo.kt`、AndroidManifest、网络配置、Gradle 依赖、测试及构建工作流。不要以重构、格式化、删除未用代码为由修改这些文件。
- `LoginScreen.kt` 可改输入框与布局；Activity 启动、account/password/manual 参数、ActivityResult、`SsoCookieHandoff.take()`、`vm.loginWithSsoCookie()` 和密码清空逻辑必须保留。
- 学校网页登录是认证协议的一部分，不能在 UI 改版时隐藏或删除备用入口，也不能调整 WebView、Cookie、User-Agent、回跳、等待超时和受保护 API 校验。
- 首次启动进入今日；无缓存引导去“我的”；有缓存直接展示；登录按需刷新；底栏顺序和智能助手子页保留。
- 包名 `cn.yzu.timetable`、缓存位置和格式、周次与单双周计算不变；不增加 AI 问答和新的服务器。
- 如需求确需改变保护区，先说明必要性并等待用户明确授权；不要自行扩展 UI 任务范围。

## 验收

在 `codex/ui-` 前缀独立分支改 UI，不直接推送交接基线 `codex/urp-timetable`。Actions 对 UI 分支及其 PR 自动对比远端基线执行范围检查。执行 `node scripts/check-ui-scope.cjs <交接提交SHA>`、`node --test scripts/test-*.cjs`、`./gradlew testDebugUnitTest assembleDebug lintDebug`，并按交接文档完成真机回归。禁止修改保护脚本或测试以绕过检查。路径检查只验证改动范围，不证明 UI 回调语义正确，仍需审查 diff 和实际操作。

本目录是独立仓库；所有提交和上传仅限 `YZUA/` 内，无账号、密码、Cookie、票据、真实成绩和真实课表。
