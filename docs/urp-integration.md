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

- `SsoLoginActivity`：学校网页登录，精确匹配教务主机和 `/index`；不注入密码、Cookie 或登录脚本，不忽略 SSL 错误。
- `SsoCookieHandoff`：一次性进程内交接 Cookie，不经 Intent 或 Bundle。
- `UrpHttpDataSource`：IO 线程请求；每次登录独立 CookieJar / 会话令牌，退出取消旧 Call 并废弃 CookieJar；旧响应不能清理或污染新账号会话。
- `UrpParser`：课表、成绩和 FullCalendar 事件解析；错误响应不冒充空列表，成绩关闭会抛出明确提示。
- `AppViewModel`：独立的课表 / 成绩 / 考试错误；查询范围切换；取消旧请求和退出时阻止旧 UI 回写。

## 隐私与生命周期

API Cookie 只驻留内存，不写入日志或 Git。Android 系统 WebView 在认证期间可能使用磁盘 Cookie 存储；App 在登录开始、完成、销毁时清理 Cookie 和网页缓存。进程被强制终止时销毁回调不保证执行，因此不能声称 WebView 全过程绝不落盘。App 不调用 CookieManager.flush 保存登录态，仅在删除 Cookie 后刷新删除状态。

测试中的身份、课程、成绩与 Cookie 均为合成数据。浏览器观察到的真实个人信息未写入本仓库。

## 考试实测限制

当前账号没有考试，页面明确显示“还没有和你相关的考试信息！”。直接导航 detail 的响应并未提供可验证的非空 JSON，所以代码优先识别考试页面的明确空状态；有考试时再带 XHR 请求头及 FullCalendar `start/end` 参数查询事件。

非空事件解析支持 FullCalendar 的 `title/start/end`，以及可能存在的地点/备注字段。这部分只有合成契约测试，不能宣称真实考试地点、座位号、非空响应格式已经验证。遇到未识别响应会显示错误，需以学校发布考试后的实际响应继续适配。

## 尚待完成

- 真机或模拟器上完成一次学校 SSO 登录和三个查询的端到端验证。
- 考试非空样例和历年成绩跨页真实数据验证（自动化已覆盖合成多页）。
- 离线持久化、用户设置学期首周日期、HITA 逐页视觉验收。

参考项目仅用于协议与界面研究：HITA、urp-core、MyStudyHelper；没有引入其他学校后端，不复制 GPL 协议实现代码。
