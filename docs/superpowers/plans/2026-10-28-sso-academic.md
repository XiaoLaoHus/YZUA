# SSO、成绩与考试增量计划及执行记录

目标：在 YZUA 独立目录的现有 Kotlin/Compose 工程中接入扬大 SSO 网页会话，提供课表、成绩及考试查询。用户已授权开发、学校只读查询、推送 YZUA 和 GitHub Actions 构建。

- [x] 浏览器核对 SSO `/sigin`、课表 GET、本学期及历年成绩动态接口、考试页 events 地址。
- [x] 先补回归测试并观察失败，再修复：等级/未公布成绩、错误与空列表、动态分页、退出旧请求、会话 Cookie 隔离、SSO 精确回调、全部学期范围切换。
- [x] WebView 登录、独立内存 API 会话、并行只读查询、刷新及分别报错、成绩/考试页接入。
- [ ] 最终单元测试、APK、Lint 和提交同版本 GitHub Actions。

裁决：继续在用户指定 YZUA 目录和 codex/urp-timetable 分支修改；不迁移或上传整个工作区。
裁决：SSO 页面由用户在学校网页登录；不代填账号密码，不将浏览器 Cookie 复制到文件。
裁决：考试以真实页面空状态为可信证据；非空字段仅作为 FullCalendar 合约适配，明确保留未实测边界。
裁决：API Cookie 只驻留内存，但系统 WebView 在登录期间可能写入磁盘；使用生命周期清理并如实说明异常退出限制。

独立审查已指出共享 CookieJar 在旧请求完成后可能污染新会话；已用独立 SessionTransport、废弃 CookieJar、取消旧 Call 修复，并添加晚到响应回归测试 RED→GREEN。

最终本地验证（2026-10-28）：`testDebugUnitTest assembleDebug lintDebug --offline` 成功，31 tests / 0 failures，Lint 0 errors / 2 warnings，Debug APK 16,962,558 bytes。GitHub Actions 待本次推送后确认；未连接 Android 设备，因此不宣称 App 端到端实测通过。
延后 Minor：极端情况下服务器重复第一页的分页去重检查；当前分页有总条数上限和空页/100页保护。
