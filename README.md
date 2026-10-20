# 登录模块开发提示词

## 一、角色与目标

你是一名资深后端工程师。请为我开发一个**登录模块**，覆盖后端、数据库与安全防护，代码需可直接运行、可测试、可扩展。


---

## 二、功能需求

### 1. 核心认证
- 用户名 + 密码登录
- 密码使用 **bcrypt（cost ≥ 12）** ，禁止明文或可逆加密
- 登录成功返回 **JWT Access Token（15 分钟）+ Refresh Token（7 天，HttpOnly + Secure + SameSite Cookie）**
- 提供 `/auth/refresh` 刷新令牌接口，支持令牌轮换（Refresh Token Rotation）与重放检测

### 2. 注册与账号管理
- 忘记密码 / 重置密码（重置令牌一次性、有效期 30 分钟）
- 修改密码后使所有已签发令牌失效

### 3. 安全防护
- **登录失败限流**：同一账号/IP 连续失败 5 次锁定 15 分钟（Redis 计数）
- **接口限流**：登录接口每 IP 每分钟 ≤ 10 次
- **验证码**：连续失败 3 次后要求图形验证码或滑块验证
- **防暴力破解**：统一错误提示"用户名或密码错误"，不泄露账号是否存在
- 敏感操作（改密）要求二次验证

---

## 三、接口设计（RESTful）

请按以下契约实现，并输出 OpenAPI 文档：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 登出（吊销 Refresh Token） |
| POST | `/api/auth/refresh` | 刷新令牌 |
| POST | `/api/auth/forgot-password` | 发起重置 |
| POST | `/api/auth/reset-password` | 提交重置 |
| GET  | `/api/auth/me` | 获取当前用户信息 |
| GET  | `/api/auth/sessions` | 登录设备列表 |

**统一响应格式：**
```json
{ "code": 0, "message": "ok", "data": { } }
```

错误码需区分：参数错误、认证失败、账号锁定、令牌过期、权限不足等。

---

## 八、约束与禁止项

- 禁止在日志中输出密码、令牌原文
- 禁止将 JWT 存入 localStorage（Access Token 可存内存，Refresh Token 用 HttpOnly Cookie）
- 禁止硬编码密钥，所有密钥从环境变量读取
- 如遇需求歧义，**先列出假设再实现**，不要静默省略