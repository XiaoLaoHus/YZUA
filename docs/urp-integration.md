# 扬大 SSO / URP 接入说明

## 已核对接口（2026-10-28）

Base URL：`http://ydjwxs.yzu.edu.cn`。学校当前使用 HTTP 教务入口；SSO 登录页为 HTTPS，应用不降低证书校验。

| 功能 | 页面与查询 | 已核对情况 |
| --- | --- | --- |
| SSO | `/sigin` → `https://sso.yzu.edu.cn/login` → 教务 `/index` | 无 Cookie 重定向及浏览器有效会话已核对 |
| 本学期课表 | GET `/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback` | 页面脚本确认，无 `planCode` |
| 本学期成绩 | 页面 `/student/integratedQuery/scoreQuery/thisTermScores/index`；GET `/student/integratedQuery/scoreQuery/{动态段}/thisTermScores/data` | 页面显示数据，脚本确认 `[state,list]`、发布状态与等级字段 |
| 全部学期成绩 | 页面 `/student/integratedQuery/scoreQuery/allTermScores/index`；POST `/student/integratedQuery/scoreQuery/{动态段}/allTermScores/data` | 脚本和查询表单确认，动态段不可写死 |
| 考试安排 | 页面 `/student/examinationManagement/examPlan/index`；GET `/student/examinationManagement/examPlan/detail` | 页面脚本声明 FullCalendar events；当前页面无相关考试 |

全部学期成绩查询使用页面表单字段，`zxjxjhh` 为空表示全部学期，`cjlx` 沿用页面默认，`kch` / `kcm` 为空；分页字段为 `pageNum` 和 `pageSize`。响应为 `list.records` 与 `list.pageContext.totalCount`，记录包含 `KCH/KXH/KCM/XF/KCSXMC/KCCJ/LRZTDM/CJLRFSDM/DJM/ZXJXJHM`。这段 POST 只是成绩查询，不改学校数据。

## 代码结构

- `SsoLoginActivity`：原生填表认证或用户选择的学校网页登录。仅向精确 HTTPS `sso.yzu.edu.cn/login` 注入本次凭据；先选择可见密码框所在表单内的用户名，等待 Angular 校验及按钮 title / disabled 门槛更新，再单次点击官方按钮，由学校执行验证码、密码加密和提交，禁止直接调用 `form.submit()`。保持正常视口，原生遮罩防止隐形网页误触。检测验证挑战、错误、主文档网络和证书失败，保留原会话的可见网页登录入口；不忽略 SSL 错误。精确匹配教务 `/index`、`/mobile/index` 后，读取课表 API 路径 Cookie，用一致的 User-Agent 验证受保护课表接口；响应必须含 `xkxx` 数组才交接含轮换结果的 Cookie 并返回 App。认证拒绝最多重新读取 Cookie 两次；原生认证等待上限 60 秒，API 验证上限 25 秒，超时或失败停止进度条。“取消登录”和系统返回键关闭认证，销毁时清理 WebView；手动模式不限制用户填写时间。“我的 → 退出登录”只废弃本地 API 会话。
- `SsoCookieHandoff`：一次性进程内交接 Cookie，不经 Intent 或 Bundle。
- `UrpHttpDataSource`：IO 线程请求；每次登录独立 CookieJar / 会话令牌，退出取消旧 Call 并废弃 CookieJar；旧响应不能清理或污染新账号会话。
- `UrpParser`：课表、成绩和 FullCalendar 事件解析；错误响应不冒充空列表，成绩关闭会抛出明确提示。
- `AppViewModel`：独立的课表 / 成绩 / 考试错误；查询范围切换；取消旧请求和退出时阻止旧 UI 回写。

## 隐私与生命周期

API Cookie 只驻留内存，不写入日志或 Git。Android 系统 WebView 在认证期间可能使用磁盘 Cookie 存储；App 在登录开始、完成、销毁时清理 Cookie 和网页缓存。进程被强制终止时销毁回调不保证执行，因此不能声称 WebView 全过程绝不落盘。App 不调用 CookieManager.flush 保存登录态，仅在删除 Cookie 后刷新删除状态。

测试中的身份、课程、成绩与 Cookie 均为合成数据。浏览器观察到的真实个人信息未写入本仓库。

## 登录失败诊断（2026-10-28）

旧实现仅凭首页 URL 和非空 Cookie 结束 WebView，再要求 API 首页存在桌面注销链接；这些条件不能证明移动端 API 会话已经建立。当前用受保护课表响应验证；已在浏览器有效会话中只读确认响应为 JSON 且 `xkxx` 为数组。未连接 Android 设备，不能据此断言手机上的失效根因或端到端已修复。

- `SSO-COOKIE-EMPTY`：没有读到课表 API 路径对应的 Cookie。
- `SSO-HTTP-xxx`：API 返回认证拒绝或重定向；不记录跳转地址及其票据。
- `SSO-LOGIN-PAGE` / `SSO-PROBE-LOGIN`：API 返回登录页或登录标记。
- `SSO-PROBE-FORMAT`：响应不是已核对的课表 JSON 结构，不当作有效会话。
- `SSO-TIMEOUT` / `SSO-PROBE-TIMEOUT`：统一认证回跳或教务会话验证超时，停止进度条，可手动重试。
- `SSO-CHALLENGE` / `SSO-REJECTED`：学校要求验证码、二次认证或页面显示拒绝；只返回固定状态码，不导出页面里的账号、票据或错误原文。
- `SSO-NETWORK` / `SSO-TLS` / `SSO-NAVIGATION`：主文档连接失败、证书错误或未支持的跳转。

2026-10-28 无限等待回归：公开 SSO Angular bundle 中的官方 `onSubmit` 先调用校验接口，必要时展示验证码，然后生成加密密码字段并提交。旧自动化同任务填表并点击，且将点击当作提交完成，缺少之后的结果检测。现将填表和点击分为不同任务，在执行可能导致回跳的点击前记录单次提交，防止导航丢失 JavaScript 回调后重发密码。Node 测试实际执行 Kotlin 生成的 JavaScript，覆盖表单定位、框架异步校验、title 门槛、禁止原生裸提交、单次点击、可信来源和错误状态；单元测试覆盖等待期限和终态。Android 真机上的学校认证仍需复测。

本轮 36 项单元测试、Debug APK、Lint（0 errors / 2 warnings）通过；新增受保护接口验证、错误诊断不泄露票据、Cookie 轮换交接与登录标记拒绝测试。

## 考试实测限制

当前账号没有考试，页面明确显示“还没有和你相关的考试信息！”。直接导航 detail 的响应并未提供可验证的非空 JSON，所以代码优先识别考试页面的明确空状态；有考试时再带 XHR 请求头及 FullCalendar `start/end` 参数查询事件。

非空事件解析支持 FullCalendar 的 `title/start/end`，以及可能存在的地点/备注字段。这部分只有合成契约测试，不能宣称真实考试地点、座位号、非空响应格式已经验证。遇到未识别响应会显示错误，需以学校发布考试后的实际响应继续适配。

## 尚待完成

- 真机或模拟器上完成一次学校 SSO 登录和三个查询的端到端验证。
- 考试非空样例和历年成绩跨页真实数据验证（自动化已覆盖合成多页）。
- 新版本的离线缓存、用户设置学期首周/结束日期和“今日”本机日期筛选已实现；HITA 逐页视觉验收仍待完成。

参考项目仅用于协议与界面研究：HITA、urp-core、MyStudyHelper；没有引入其他学校后端，不复制 GPL 协议实现代码。

## 离线缓存与刷新策略

`AcademicCache` 只写入课表、成绩、考试及学期日期，使用应用私有 `noBackupFilesDir/academic-v1.json` 和临时文件原子替换；系统备份已关闭。Cookie、密码、`UserSession.token` 不进入缓存。启动只恢复缓存，不自动向 URP 发请求；在“我的”中统一认证后才刷新数据。退出登录废弃内存 API 会话但保留缓存，便于离线查看；“清除本地数据 / 更换账号”才删除课表、成绩、考试和学期设置。由于当前受保护 URP 响应没有可靠暴露学号，SSO 再登录成功时会先隔离旧快照，防止手填学号与实际 SSO 账号不一致造成串号；新查询成功后再写入缓存。

“今日”用本机日期计算星期和学期周次，再按课程 `weekNumbers` 过滤单双周并按起始节次排序。学期日期可以手动校准；学校首页返回的教学周次只作为未确认的建议。新学期开始、保存的学期结束日和过期日期显示提醒条，不自动弹登录界面。成绩或考试刷新遇到 SSO 失效时保留整个旧快照并要求用户到“我的”重新登录。
