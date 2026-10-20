# movie-ticket-master 电影购票系统

## 项目概述

基于 Spring Boot + MyBatis-Plus + Redis + MySQL 构建的电影购票系统后端服务。

- 基础路径：`http://localhost:8085`
- 统一响应格式：`package org.javaup.dto.Result`
- 认证方式：
  - 暂时不管

---

## 模块列表

| 模块 | 说明 |
|------|------|
| movie-common | 公共模块（Result、BaseCode、全局异常处理） |
| movie-core-service | 核心业务服务（认证、电影、影院、场次、订单） |
| movie-redis-framework | Redis 基础框架 |
| movie-redis-rate-limit-framework | 限流框架 |
| movie-id-generator | 分布式 ID 生成器 |
| movie-mq-* | 消息队列框架 |

---

## 认证模块（已实现）

详见项目登录模块开发文档。

---

## 电影模块接口

### 管理端接口（/admin/movie）

#### 1. 新增电影

```
POST /admin/movie
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 电影名称，最长 100 |
| director | String | 是 | 导演，最长 100 |
| duration | Integer | 是 | 时长（分钟），≥ 1 |
| releaseDate | String | 是 | 上映日期，格式 yyyy-MM-dd |
| posterUrl | String | 是 | 海报图片 URL |
| description | String | 否 | 剧情简介 |
| categoryIds | List<Long> | 否 | 分类 ID 列表（动作/爱情/科幻 等） |
| actorIds | List<Long> | 否 | 演员 ID 列表 |

**响应**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "流浪地球2",
    "director": "郭帆",
    "duration": 173,
    "releaseDate": "2023-01-22",
    "posterUrl": "https://...",
    "description": "太阳即将毁灭...",
    "status": 1
  }
}
```

---

#### 2. 修改电影

```
PUT /admin/movie/{id}
Content-Type: application/json
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 电影 ID |

**请求体**：同新增，所有字段可选（只传要改的字段）。

**响应**

```json
{ "success": true }
```

---

#### 3. 删除电影

```
DELETE /admin/movie/{id}
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 电影 ID |

**业务规则**

- 若该电影存在未结束的场次（schedule.status = 1 且 end_time > NOW()），返回 400 错误
- 若存在场次但均已结束，允许删除（同时级联删除 schedule、ticket 相关记录）

**响应（无场次）**

```json
{ "success": true }
```

**响应（有场次）**

```json
{
  "success": false,
  "errorMsg": "该电影存在进行中的场次，无法删除"
}
```

---

#### 4. 上/下架电影

```
PATCH /admin/movie/{id}/status
Content-Type: application/json
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 电影 ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 1=上架（热映/即将上映），0=下架 |

```json
{ "status": 0 }
```

**响应**

```json
{ "success": true }
```

---

#### 5. 分页查询电影（管理端）

```
GET /admin/movie/page
```

**请求参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNo | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |
| keyword | String | 否 | - | 按电影名称模糊搜索 |
| status | Integer | 否 | - | 1=上架 0=下架；不传则全部 |

**响应**

```json
{
  "success": true,
  "total": 25,
  "data": [
    {
      "id": 1,
      "name": "流浪地球2",
      "director": "郭帆",
      "duration": 173,
      "releaseDate": "2023-01-22",
      "posterUrl": "https://...",
      "status": 1,
      "categoryNames": ["科幻", "冒险"],
      "sessionCount": 12
    }
  ]
}
```

---

#### 6. 电影详情（管理端）

```
GET /admin/movie/{id}
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 电影 ID |

**响应**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "流浪地球2",
    "director": "郭帆",
    "duration": 173,
    "releaseDate": "2023-01-22",
    "posterUrl": "https://...",
    "description": "太阳即将毁灭...",
    "status": 1,
    "categories": [
      { "id": 3, "name": "科幻" },
      { "id": 5, "name": "冒险" }
    ],
    "actors": [
      { "id": 1, "name": "吴京", "role": "刘培强" },
      { "id": 2, "name": "刘德华", "role": "图恒宇" }
    ],
    "sessions": [
      {
        "sessionId": 1001,
        "cinemaName": "万达影城 CBD 店",
        "hallName": "IMAX 厅",
        "startTime": "2026-10-25 19:30",
        "endTime": "2026-10-25 22:23",
        "price": 45.00,
        "availableSeat": 87
      }
    ]
  }
}
```

---

### 用户端接口（/movie）

#### 7. 正在热映

```
GET /movie/now-showing
```

**业务规则**

- 查询 `releaseDate <= 今天 <= 最后一场 schedule.end_time` 且 `status = 1` 的电影
- 按上映日期降序排列
- 支持分页

**请求参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNo | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |
| city | String | 否 | - | 按城市过滤（可选） |

**响应**

```json
{
  "success": true,
  "total": 18,
  "data": [
    {
      "id": 1,
      "name": "流浪地球2",
      "posterUrl": "https://...",
      "duration": 173,
      "releaseDate": "2023-01-22",
      "minPrice": 35.00,
      "categories": ["科幻", "冒险"],
      "cinemaCount": 12
    }
  ]
}
```

---

#### 8. 即将上映

```
GET /movie/coming-soon
```

**业务规则**

- 查询 `releaseDate > 今天` 且 `status = 1` 的电影
- 按上映日期升序排列

**请求参数**：同「正在热映」。

**响应**：同「正在热映」（不返回 minPrice 和 cinemaCount）。

---

#### 9. 电影详情（用户端）

```
GET /movie/{id}
```

**业务规则**

- 管理端返回全部字段；用户端只返回 `status = 1` 的电影
- 额外返回：当前最低票价、有排片的影院数量

**响应**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "流浪地球2",
    "director": "郭帆",
    "duration": 173,
    "releaseDate": "2023-01-22",
    "posterUrl": "https://...",
    "description": "太阳即将毁灭...",
    "categories": ["科幻", "冒险"],
    "actors": [
      { "id": 1, "name": "吴京", "avatar": "https://..." },
      { "id": 2, "name": "刘德华", "avatar": "https://..." }
    ],
    "minPrice": 35.00,
    "cinemaCount": 12
  }
}
```

---

#### 10. 搜索电影

```
GET /movie/search
```

**请求参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | String | 是 | - | 搜索关键字（匹配片名/导演/演员） |
| pageNo | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 10 | 每页条数 |
| categoryId | Long | 否 | - | 按分类过滤 |

**业务规则**

- 仅返回 `status = 1` 的电影
- 匹配范围：movie.name、movie.director、actor.name

**响应**

```json
{
  "success": true,
  "total": 3,
  "data": [
    { "id": 1, "name": "流浪地球2", "posterUrl": "...", "duration": 173 }
  ]
}
```

---

#### 11. 查哪些影院有该电影场次（购票入口）

```
GET /movie/{id}/cinemas
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 电影 ID |

**请求参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| city | String | 否 | - | 城市名；不传返回全部 |
| date | String | 否 | 今天 | 查询哪一天的场次，格式 yyyy-MM-dd |

**业务规则**

- 聚合查询：某电影在指定日期有排片的所有影院
- 影院下附：该电影在该影院当天的最低票价、首个场次时间、剩余场次数量

**响应**

```json
{
  "success": true,
  "data": [
    {
      "cinemaId": 10,
      "cinemaName": "万达影城 CBD 店",
      "city": "北京",
      "address": "建国门外大街 ...",
      "phone": "010-88888888",
      "minPrice": 38.00,
      "firstSessionTime": "2026-10-25 10:00",
      "sessionCount": 6
    },
    {
      "cinemaId": 11,
      "cinemaName": "CGV 影城 三里屯店",
      "city": "北京",
      "minPrice": 42.00,
      "firstSessionTime": "2026-10-25 11:30",
      "sessionCount": 4
    }
  ]
}
```

---

#### 12. 查某影院某天该电影的场次

```
GET /movie/{id}/sessions
```

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 电影 ID |

**请求参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| cinemaId | Long | 是 | - | 影院 ID |
| date | String | 否 | 今天 | 查询日期，格式 yyyy-MM-dd |

**业务规则**

- 只返回指定日期内 `start_time` 在当天 00:00:00 ~ 23:59:59 之间的场次
- 按开始时间升序排列
- 每个场次附带：影厅信息、票价、剩余座位数

**响应**

```json
{
  "success": true,
  "data": [
    {
      "sessionId": 1001,
      "hallId": 201,
      "hallName": "IMAX 厅",
      "hallType": 2,
      "startTime": "2026-10-25 10:00",
      "endTime": "2026-10-25 12:53",
      "price": 38.00,
      "totalSeat": 210,
      "availableSeat": 156,
      "status": 1
    },
    {
      "sessionId": 1002,
      "hallId": 202,
      "hallName": "4DX 厅",
      "hallType": 3,
      "startTime": "2026-10-25 14:00",
      "endTime": "2026-10-25 16:53",
      "price": 58.00,
      "totalSeat": 180,
      "availableSeat": 92,
      "status": 1
    }
  ]
}
```

---

## 订单模块接口

### 业务规则总览

| 订单状态(status) | 说明 | 可执行操作 |
|-----------------|------|-----------|
| 0 | 待支付 | 支付 / 取消 |
| 1 | 已支付 | 使用 / 申请退款 |
| 2 | 已取消 | — |
| 3 | 退款中 | — |
| 4 | 已退款 | — |
| 5 | 已完成 | — |

**座位状态联动**（schedule_seat.status）：

| 动作 | schedule_seat.status 变化 |
|------|--------------------------|
| 下单 | 0（可售）→ **1（锁定）** |
| 支付成功 | 1（锁定）→ **2（已售）** |
| 待支付取消 | 1（锁定）→ **0（可售）** |
| 已支付退款 | 2（已售）→ **0（可售）** |

**超时取消**：下单后 15 分钟未支付自动取消（定时任务，暂不实现）。

---

### 用户端接口（/order，必须登录）

#### 1. 创建订单（选座下单）

```
POST /order
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scheduleId | Long | 是 | 场次 ID |
| seatIds | List<Long> | 是 | 选中的 schedule_seat ID 列表（1~10 张） |

**响应**

```json
{
  "success": true,
  "data": {
    "id": 1000000000001,
    "orderNo": "202610201234567890123",
    "status": 0,
    "seatCount": 3,
    "totalAmount": 180.00,
    "discount": 0.00,
    "payAmount": 180.00,
    "expireAt": "2026-10-20 18:30:00"
  }
}
```

**业务规则**：
- 必须登录（从 ThreadLocal UserHolder 取 userId）
- 所选座位必须全部为可售（schedule_seat.status=0）
- 任一座位已被他人锁定/售出 → 整单失败，全部回滚
- 一次性锁全部座位（1→15 个）
- 默认 15 分钟过期

---

#### 2. 我的订单列表

```
GET /order/my?pageNo=1&pageSize=10&status=1
Authorization: Bearer <access_token>
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNo | Integer | 否 | 默认 1 |
| pageSize | Integer | 否 | 默认 10 |
| status | Integer | 否 | 按状态过滤：0 待支付 / 1 已支付 / 2 已取消 |

**响应**

```json
{
  "success": true,
  "data": {
    "records": [
      {
        "id": 1000000000001,
        "orderNo": "202610201234567890123",
        "movieName": "流浪地球2",
        "cinemaName": "万达影城 CBD 店",
        "hallName": "IMAX 厅",
        "startTime": "2026-10-20 20:00",
        "seatCount": 3,
        "payAmount": 180.00,
        "status": 1,
        "createdAt": "2026-10-20 18:15:00"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 10
  }
}
```

---

#### 3. 订单详情（含座位明细）

```
GET /order/{id}
Authorization: Bearer <access_token>
```

**响应**

```json
{
  "success": true,
  "data": {
    "id": 1000000000001,
    "orderNo": "202610201234567890123",
    "status": 1,
    "movieName": "流浪地球2",
    "moviePoster": "https://...",
    "cinemaName": "万达影城 CBD 店",
    "cinemaAddress": "朝阳区建国路 88 号",
    "hallName": "IMAX 厅",
    "hallType": 2,
    "startTime": "2026-10-20 20:00",
    "endTime": "2026-10-20 22:53",
    "price": 60.00,
    "seatCount": 3,
    "totalAmount": 180.00,
    "discount": 0.00,
    "payAmount": 180.00,
    "payTime": "2026-10-20 18:16:22",
    "items": [
      { "seatCode": "A5", "rowNo": 1, "colNo": 5, "price": 60.00 },
      { "seatCode": "A6", "rowNo": 1, "colNo": 6, "price": 60.00 },
      { "seatCode": "A7", "rowNo": 1, "colNo": 7, "price": 60.00 }
    ]
  }
}
```

**业务规则**：
- 只能查自己的订单（userId 校验）
- 含完整座位明细（order_item）

---

#### 4. 取消订单

```
POST /order/{id}/cancel
Authorization: Bearer <access_token>
```

**响应**

```json
{ "success": true, "data": null }
```

**业务规则**：
- 只能取消自己的待支付订单（status=0）
- 取消后所有锁定座位自动释放回可售（schedule_seat.status 1→0）
- 已支付 → 走退款流程

---

#### 5. 支付成功回调（模拟）

```
POST /order/{id}/pay
Authorization: Bearer <access_token>
```

**响应**

```json
{ "success": true, "data": null }
```

**业务规则**：
- 只能支付自己的待支付订单
- 支付成功 → schedule_seat.status 1（锁定）→ **2（已售）**
- schedule.available_seat 自动更新（totalSeat - 已售数）
- 设置 payTime

---

### 管理端接口（/admin/order）

#### 6. 分页查询订单

```
GET /admin/order/page?pageNo=1&pageSize=10&status=1&movieId=1&date=2026-10-20
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNo | Integer | 否 | 默认 1 |
| pageSize | Integer | 否 | 默认 10 |
| orderNo | String | 否 | 订单号精确搜索 |
| userId | Long | 否 | 用户 ID |
| movieId | Long | 否 | 电影 ID |
| cinemaId | Long | 否 | 影院 ID |
| status | Integer | 否 | 按状态过滤 |
| date | String | 否 | 按下单日期（yyyy-MM-dd）过滤 |

**响应**

```json
{
  "success": true,
  "data": {
    "records": [
      {
        "id": 1000000000001,
        "orderNo": "202610201234567890123",
        "userId": 1,
        "movieName": "流浪地球2",
        "cinemaName": "万达影城 CBD 店",
        "seatCount": 3,
        "payAmount": 180.00,
        "status": 1,
        "createdAt": "2026-10-20 18:15:00",
        "payTime": "2026-10-20 18:16:22"
      }
    ],
    "total": 1
  }
}
```

---

#### 7. 管理端订单详情

```
GET /admin/order/{id}
```

响应结构同用户端订单详情，但不限制 userId。

---

#### 8. 手动退款

```
POST /admin/order/{id}/refund
Content-Type: application/json
```

**请求体**

```json
{ "reason": "用户申请退票" }
```

**业务规则**：
- 仅允许对已支付订单（status=1）退款
- 退款后所有已售座位释放回可售（schedule_seat.status 2→0）
- schedule.available_seat 自动加回
- order.status 变为 4（已退款）
- 记录 cancelTime 和 discount（退款金额 = payAmount）

---

### order / order_item 表结构参考

#### order

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint | 主键（雪花 ID） |
| order_no | varchar(32) | 业务订单号（唯一） |
| user_id | bigint | 下单用户 |
| schedule_id | bigint | 场次 |
| movie_id | bigint | 冗余：电影 |
| cinema_id | bigint | 冗余：影院 |
| seat_count | int | 座位数 |
| total_amount | decimal(10,2) | 原价总额 |
| discount | decimal(10,2) | 优惠 |
| pay_amount | decimal(10,2) | 实付 |
| status | tinyint | 0待支付 1已支付 2已取消 3退款中 4已退款 5已完成 |
| expire_at | datetime | 支付超时时间 |
| pay_time | datetime | 支付时间 |
| cancel_time | datetime | 取消/退款时间 |
| created_at | datetime | 创建 |
| updated_at | datetime | 更新 |

#### order_item

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint | 主键 |
| order_id | bigint | 订单 |
| schedule_seat_id | bigint | 场次-座位关联 |
| seat_code | varchar(20) | 座位码（如 A5） |
| row_no | int | 行号 |
| col_no | int | 列号 |
| price | decimal(10,2) | 该座位价格（冗余） |
| created_at | datetime | 创建 |

---

## 数据表参考

### movie 电影表

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint | 主键 |
| name | varchar(100) | 电影名称 |
| director | varchar(100) | 导演 |
| duration | int | 时长（分钟） |
| release_date | date | 上映日期 |
| poster_url | varchar(255) | 海报 URL |
| description | text | 剧情简介 |
| status | tinyint | 1=上架 0=下架 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

### cinema 影院表

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint | 主键 |
| name | varchar(100) | 影院名称 |
| city | varchar(50) | 城市（索引） |
| address | varchar(255) | 地址 |
| phone | varchar(20) | 电话 |
| status | tinyint | 1=营业中 0=停业 |

### hall 影厅表

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint | 主键 |
| cinema_id | bigint | 所属影院（索引） |
| name | varchar(50) | 影厅名称（如 "IMAX 厅"） |
| type | tinyint | 1=普通 2=IMAX 3=4DX 4=VIP |
| row_count | int | 排数 |
| col_count | int | 每排座位数 |
| layout | json | 座位布局（可选，含空位标记） |

### schedule 场次表

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint | 主键 |
| movie_id | bigint | 电影 ID（索引） |
| cinema_id | bigint | 影院 ID（索引） |
| hall_id | bigint | 影厅 ID（索引） |
| start_time | datetime | 开场时间 |
| end_time | datetime | 散场时间 |
| price | decimal(10,2) | 票价 |
| total_seat | int | 总座位 |
| available_seat | int | 剩余座位 |
| status | tinyint | 1=可售 0=停售 |
| version | int | 乐观锁版本号 |

---

## 通用错误码（来自 BaseCode）

| 错误码 | 含义 |
|--------|------|
| 40000 | 参数错误 |
| 40100 | 用户名或密码错误 |
| 40101 | 令牌已过期 |
| 40102 | 令牌无效 |
| 40300 | 账号已锁定 |
| 50001 | 手机号已注册 |
| 10005 | 秒杀库存不足 |
| 0 | 业务异常兜底 |

---
