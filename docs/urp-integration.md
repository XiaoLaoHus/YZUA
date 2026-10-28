# URP 接入说明

## 已完成的边界

页面只依赖 `UrpScheduleDataSource`：

- `login(account, password)` 返回 `UserSession`，包含学号、展示名、令牌和来源标记。
- `getSchedule(session, term)` 返回统一的 `ScheduleLesson`，页面不感知 URP 字段名。
- `UrpHttpDataSource` 默认连接 `http://ydjwxs.yzu.edu.cn`，用内存 Cookie 保持会话。
- 课表请求使用扬大公开适配中的 `planCode` 和 `ajaxStudentSchedule/callback` 路径。
- `UrpParser` 支持 `xkxx/timeAndPlaceList/jcsjbs` JSON、经典 URP HTML 表格、位图周次和单双周文本。
- `DemoUrpDataSource` 保留给离线 UI 和自动化测试；应用默认使用 URP 数据源。

## 接通扬大系统所需信息

1. 默认 Base URL 是 `http://ydjwxs.yzu.edu.cn`，后续迁移可在 `UrpConfig` 替换为 HTTPS 地址。
2. 登录请求方法、路径、字段名、验证码或二次认证要求。
3. 登录成功响应中的 JWT/Session 字段和过期规则。
4. 学生课表接口路径、学期参数格式、课程 JSON/HTML 响应样例。
5. 节次时间、周次表达式和教学楼字段的映射规则。

## 推荐实现

当前实现使用 OkHttp CookieJar 保持网页会话，并在独立 parser 中解析 JSON/HTML；账号密码只用于当前登录请求，不写入日志或明文存储。服务器返回验证码、统一认证页面或 WebVPN 跳转时，应用会提示先在校园网页完成认证，不绕过验证码。

参考思路：`xzystarts/urp-core` 的会话边界、`MyStudyHelper` 的 URP 网页解析流程，以及扬大 WebVPN 课表适配中的 `planCode` 查询方式。仓库测试使用脱敏合成响应，真实账号登录仍需在校园网络/统一认证环境中验证。
