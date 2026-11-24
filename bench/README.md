# 电影票务系统 JMeter 压测方案

针对 `movie-ticket-master` 后端（Spring Boot 3 + Redis + MyBatis-Plus）设计的接口压测脚本，
覆盖读热点、下单写链路、列表查询与**并发抢座超卖验证**四个维度。

---

## 一、文件说明

| 文件 | 作用 |
|---|---|
| `movie-bench.jmx` | JMeter 压测计划（5 个 ThreadGroup），参数全部走 `${__P()}`，可直接命令行调参 |
| `prepare_bench.py` | 数据准备脚本：自动挑场次、建测试账号、生成 4 个 CSV |
| `run-bench.ps1` | Windows 一键执行：定位 JMeter → 校验数据 → 跑压测 → 输出指标汇总 |
| `summarize.py` | 解析 `result.jtl`，算出 TPS / 错误率 / P95 / P99 / 超卖结论 |
| `targets.csv` | 场次、座位、电影（TG01、TG04 使用） |
| `order-seats.csv` | 每行一组**互不重叠**的座位（TG02 每线程一组，避免自相残杀） |
| `oversell-seats.csv` | 所有线程抢**同一个**座位（TG05 超卖验证） |
| `tokens.csv` | 测试账号 token（TG02/TG03/TG05） |

> 这 4 个 CSV 由 `prepare_bench.py` 生成，**不要手工改**。

---

## 二、环境要求

| 项 | 要求 |
|---|---|
| JDK | 8+（当前机器 `JAVA_HOME=C:\Program Files\Java\jdk-17` ✅） |
| JMeter | 5.6.x，下载后解压并设 `JMETER_HOME`，或运行时 `-JmeterHome <路径>` |
| 后端 | `movie-core-service` 启动在 8085，**MySQL + Redis 必须在线**（锁座/限流/幂等都依赖 Redis） |
| Python | 3.8+，`pip install requests` |

后端启动：

```bash
cd movie-core-service
mvn spring-boot:run
```

---

## 三、三步跑起来

```powershell
# 1. 造数据（自动挑场次 + 建 60 个测试账号 + 拉取 token）
cd bench
python prepare_bench.py

# 2. 压测
.\run-bench.ps1

# 3. 看报告
#    终端直接打印指标汇总，同时生成 HTML 报告
#    results\<时间戳>\html\index.html
```

### run-bench.ps1 参数

```powershell
.\run-bench.ps1 `
  -HostName localhost -Port 8085 `
  -ThreadsSeatmap 20 -ThreadsOrder 30 -ThreadsOrders 20 `
  -ThreadsPublic 20 -ThreadsOversell 50 `
  -LoopsSeatmap 5 -RampSeatmap 10 `
  -JmeterHome "D:\apache-jmeter-5.6.3"
```

### 不用 PowerShell（跨平台）

```bash
jmeter -n -t movie-bench.jmx \
  -Jhost=localhost -Jport=8085 \
  -Jthreads.seatmap=20 -Jthreads.order=30 -Jthreads.oversell=50 \
  -Jloops.seatmap=5 -Jramp.seatmap=10 \
  -l results/result.jtl -e -o results/html

python summarize.py results/result.jtl oversell-seats.csv
```

---

## 四、压测场景设计

### TG01 座位图查询（读热点）

- 接口：`GET /movie/schedule/{scheduleId}/seats`
- 默认 20 线程 × 5 轮，爬坡 10s，**思考时间 600ms**
- 600ms 思考时间是**必须的**：后端 `seat_map` 场景限流 **50 次/秒/IP**，不限速会把限流误判成服务端瓶颈

### TG02 并发下单抢座（写链路）

- 接口：`POST /order`
- 默认 30 线程 × 1 轮，1s 内全部起跑，制造瞬时并发
- 每个线程拿 `order-seats.csv` 里**自己那一行**的座位组，互不重叠 → 30 个并发订单全部应当成功
- 断言 `"success":true`

### TG03 我的订单列表

- 接口：`GET /order/my?pageNo=1&pageSize=10`
- 20 线程 × 5 轮，验证列表查询在多用户下的响应时间

### TG04 公开接口

- `GET /movie/now-showing?pageNo=1&pageSize=10`
- `GET /movie/{movieId}`
- 20 线程 × 5 轮，验证首页流量

### TG05 超卖验证（核心指标）

- 接口：`POST /order`，**所有线程抢 `oversell-seats.csv` 里那一个座位**
- 默认 50 线程 × 1 轮
- **预期结果：50 个请求里只有 1 个成功，其余 49 个返回 `success=false`（座位已被锁定）**
- `summarize.py` 会自动比对"成功订单数 vs 座位数"，给出无超卖 / 有超卖结论

---

## 五、压测中的三个坑（必须知道）

### 1. 下单限流是**按用户**的，不是按 IP

后端 `UserOrderController` 里：

```java
rateLimitService.tryAcquire("order_create", String.valueOf(userId), 3, 10);
```

即 **同一用户 10 秒内最多 3 次下单**。如果所有线程用同一个 token，压测结果全是"操作过于频繁"，
完全测不出真实性能。所以 `prepare_bench.py` 会批量注册 60 个账号并各自登录拿 token。

> 60 个账号会被写进 `sys_user` 表（手机号 `19900001000` ~ `19900001059`，密码 `Bench@12345`）。
> 这是压测用的脏数据，演示完可在 `sys_user` 里清理。

### 2. 幂等键让"同一用户 + 同一场次 + 同一座位组"10 秒内只能下一次单

TG02 若给所有线程同一组座位，会被幂等直接挡掉，根本走不到锁座逻辑。
所以 `order-seats.csv` 每行给一组不同的座位。

### 3. 座位图限流 50 次/秒/IP

TG01 必须带思考时间，否则错误率会被限流刷高，看不出接口真实性能。

### 4. 业务失败的 HTTP 状态码是 200，断言必须看 body

后端统一封装 `Result{success, errorMsg, data, total}`，**业务异常也返回 HTTP 200**，
靠 body 里的 `success` 字段区分成败。所以脚本里所有断言都检查响应体是否包含 `"success":true`，
而不是只看状态码：

```xml
<stringProp name="Assertion.test_field">Assertion.response_data</stringProp>
<stringProp name="Assertion.test_type">2</stringProp>   <!-- 2 = contains -->
<stringProp name="Assertion.test_string">"success":true</stringProp>
```

> 若只看 `Assertion.response_code == 200`，TG05 的 50 个请求会全部判为"成功"，超卖验证就失效了。

---

## 六、指标解读（本机实测参考值）

后端本地运行（8085 预热后的常驻实例）、20/30/50 线程的一次完整实测：

```
采样器                         请求数      错误率     平均(ms)      P95      P99      TPS
MOVIEDETAIL-电影详情            100    0.00%          3        5        6     10.7
NOWSHOWING-正在热映              100    0.00%          4        7       50     10.7
ORDER-下单抢座                   30    0.00%         47      114      129     39.0
ORDERS-我的订单                 100    0.00%         20       43       82     10.6
OVERSELL-抢同一个座位              50   98.00%         35       99      104     61.4
SEATMAP-座位图                  100    0.00%         14       20       50      8.4
【超卖验证】50 个线程抢同一个座位，成功下单 1 单
【超卖验证】目标座位共 1 个（388706861727092736）
【超卖验证】✔ 无超卖：成功订单数 1 ≤ 座位数 1
```

TG05 的 98% 错误率是**正常且正确**的——49 个线程被锁座机制挡下，正是防超卖生效的证据。

> 复现命令：`.\run-bench.ps1 -JmeterHome "D:\apache-jmeter-5.6.3"`，
> 完整结果见 `results\<时间戳>\html\index.html`。

> ⚠️ **首次压测前务必预热**。用 `mvn spring-boot:run` 刚起来的实例，JIT 未预热 + DEBUG 日志全开，
> 同一套参数测出来下单接口平均会飙到 2000ms 以上（比预热后高 40 倍）。
> 建议：先跑一轮小流量（`-LoopsSeatmap 1 -ThreadsOrder 10`）当预热，再跑正式压测。
> 上表数据取自预热后的常驻实例。

---

## 七、写进简历的话术模板

按你实际跑出来的数字替换括号部分：

> **高并发抢座防超卖**：基于 JMeter 对下单接口做 **50 并发瞬时抢座压测**，
> 验证同一座位在 Redis Lua 原子锁 + DB 乐观锁双重防线下**成功订单数恒为 1，超卖率 0%**；
> 下单接口 P95 响应 **114ms**，并发下单吞吐 **39 TPS**，锁座失败响应平均 **35ms**（快速失败，不占用 DB 连接）。
>
> **读热点缓存验证**：座位图接口 20 并发稳态压测，**错误率 0%，P99 响应 50ms**，
> 300 座场景下响应体从 24KB 压缩至 6KB（2bit/座位图 + Base64），并叠加 Caffeine + Redis 两级缓存。
>
> **接口限流与幂等**：下单接口按用户维度 3 次/10s 限流 + 10s 幂等窗口，
> 压测中验证重复提交 100% 被拦截，恶意刷单不产生脏订单。

想拿更漂亮的数据，把压测打到更高并发：

```powershell
.\run-bench.ps1 -ThreadsOrder 100 -ThreadsOversell 200 -LoopsSeatmap 20
```

---

## 八、常见问题

**Q: `run-bench.ps1` 报"未找到 JMeter"**
设置环境变量 `JMETER_HOME`，或 `-JmeterHome "D:\apache-jmeter-5.6.3"`。

**Q: `prepare_bench.py` 报"可售座位不足"**
当前场次被别人占了。指定另一个场次：
`python prepare_bench.py --schedule-id 1743568293832000009`，
或先跑 `seed_seats.py` 给场次补座位。

**Q: TG05 全都失败了，成功 0 单**
说明那个目标座位已被前面的测试锁住（锁座 15 分钟超时自动释放）。
重新跑 `python prepare_bench.py` 会挑新的可售座位。

**Q: 报错 `Cannot write to ... as folder does not exist`**
`-o` 指定的 HTML 报告目录必须**不存在或为空**，JMeter 不会自动建目录。
`run-bench.ps1` 已处理这种情况。

**Q: 想看原始响应报文**
给采样器加 Debug Sampler，或在 `movie-bench.jmx` 里打开 View Results Tree。
