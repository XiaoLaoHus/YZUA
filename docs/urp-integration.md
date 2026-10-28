# URP 接入说明

## 已完成的边界

页面只依赖 `UrpScheduleDataSource`：

- `login(account, password)` 返回 `UserSession`，包含学号、展示名、令牌和来源标记。
- `getSchedule(session, term)` 返回统一的 `ScheduleLesson`，页面不感知 URP 字段名。
- 当前 `DemoUrpDataSource` 仅用于 UI 验收，所有课程都标记为“演示数据”。

## 接通扬大系统所需信息

1. URP 登录 Base URL（例如 `https://jwgl.example.edu.cn`）。
2. 登录请求方法、路径、字段名、验证码或二次认证要求。
3. 登录成功响应中的 JWT/Session 字段和过期规则。
4. 学生课表接口路径、学期参数格式、课程 JSON/HTML 响应样例。
5. 节次时间、周次表达式和教学楼字段的映射规则。

## 推荐实现

使用 Retrofit + OkHttp 创建 `UrpHttpDataSource`：登录后将 JWT 放入 `Authorization: Bearer <token>`，把响应 DTO 映射为 `ScheduleLesson`。如果扬大 URP 只提供网页端表单，则用 OkHttp CookieJar 保持会话，并在独立 parser 中解析 HTML；账号密码仅在请求期间使用，不写入日志或明文存储。

参考思路：`xzystarts/urp-core` 的 JWT 用户会话与学生课表查询、`MyStudyHelper` 的 URP 网页解析流程。真实接口启用前应使用脱敏的登录与课表响应样例补充契约测试。
