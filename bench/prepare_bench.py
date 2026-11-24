#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JMeter 压测数据准备脚本
=========================
为 bench/movie-bench.jmx 生成三个数据文件：

  targets.csv          场次/座位/电影（TG01 座位图、TG04 公开接口）
  order-seats.csv      每行一组互不重叠的座位（TG02 并发下单，每线程一组）
  oversell-seats.csv   所有线程抢同一组座位（TG05 超卖验证）
  tokens.csv           N 个测试账号的 token（TG02/TG03/TG05）
  pay-orders.csv       待支付订单 orderId;token（TG06 支付确认）
  cancel-orders.csv    待支付订单 orderId;token（TG07 取消订单释放座位）
  refund-orders.csv    已支付订单 orderId;token（TG08 退款申请）
  login-users.csv      登录压测账号 phone;password（TG09 登录并发幂等）

用法：
  python prepare_bench.py                       # 自动挑场次 + 建 60 个测试账号
  python prepare_bench.py --users 30 --seats-per-order 2
  python prepare_bench.py --schedule-id 1743568293832000009

依赖：requests
"""

import argparse
import base64
import csv
import os
import sys
import time

import requests

BENCH_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_PASSWORD = "Bench@12345"
PHONE_BASE = 19900001000  # 测试账号手机号起点


# ---------------------------------------------------------------- 工具函数

def call(method, url, **kwargs):
    """调用后端接口并解包 Result{success,errorMsg,data}"""
    resp = requests.request(method, url, timeout=15, **kwargs)
    if resp.status_code != 200:
        raise HttpError(method, url, resp.status_code, resp.text[:200])
    body = resp.json()
    if not body.get("success"):
        raise RuntimeError("%s %s -> %s" % (method, url, body.get("errorMsg")))
    return body.get("data")


class HttpError(RuntimeError):
    """HTTP 非 200 响应"""

    def __init__(self, method, url, status, body):
        super().__init__("%s %s -> HTTP %s %s" % (method, url, status, body))
        self.status = status
        self.body = body


def decode_seat_status(bitmap_b64, seat_count):
    """解码 2bit/座 位图：00=可售 01=锁定 10=已售 11=无此座位，大端序"""
    raw = base64.b64decode(bitmap_b64)
    status = []
    for i in range(seat_count):
        status.append((raw[i // 4] >> ((3 - i % 4) * 2)) & 0x03)
    return status


def seat_positions_json(positions):
    """把 [(rowNo, colNo), ...] 转成下单接口需要的 seats JSON 数组"""
    return "[%s]" % ",".join('{"rowNo":%d,"colNo":%d}' % (r, c) for r, c in positions)


def pick_schedule(base_url, wanted_id=None):
    """挑一个可售且座位充足的场次，返回 (scheduleId, movieId)"""
    if wanted_id:
        # 指定场次：直接读座位图确认可用
        sm = call("GET", "%s/movie/schedule/%s/seats" % (base_url, wanted_id))
        return str(sm["scheduleId"]), str(sm["movieId"])

    movies = call("GET", "%s/movie/now-showing?pageNo=1&pageSize=20" % base_url)
    best = None
    for mv in movies.get("records", []):
        cinemas = call("GET", "%s/movie/%s/cinemas" % (base_url, mv["id"])) or []
        for cin in cinemas:
            sessions = call(
                "GET",
                "%s/movie/%s/sessions?cinemaId=%s" % (base_url, mv["id"], cin["cinemaId"]),
            ) or []
            for s in sessions:
                if str(s.get("status")) != "1":
                    continue
                if int(s.get("availableSeat") or 0) < 60:
                    continue
                cand = (str(s["scheduleId"]), str(mv["id"]), int(s["availableSeat"]))
                if best is None or cand[2] > best[2]:
                    best = cand
    if best is None:
        raise RuntimeError("没有找到座位充足的可售场次，请先造数据或用 --schedule-id 指定")
    return best[0], best[1]


def fetch_available_seats(base_url, schedule_id):
    """返回该场次座位图和所有可售座位的位置 [(rowNo, colNo), ...]（按座位图索引顺序）"""
    sm = call("GET", "%s/movie/schedule/%s/seats" % (base_url, schedule_id))
    row_count = int(sm["rowCount"])
    col_count = int(sm["colCount"])
    status = decode_seat_status(sm["seatStatusBitmap"], row_count * col_count)
    available = []
    for i, s in enumerate(status):
        if s == 0:
            available.append((i // col_count + 1, i % col_count + 1))
    return sm, available


def ensure_user(base_url, phone, password):
    """注册（已存在则忽略）后登录，返回 accessToken"""
    payload = {"phone": phone, "password": password, "nickname": "bench_%s" % phone[-4:]}
    try:
        call("POST", "%s/auth/register" % base_url, json=payload)
    except HttpError as e:
        # 401/409 视为"已注册"，直接走登录
        if e.status not in (400, 401, 409):
            raise
    except RuntimeError:
        # 业务层提示"该手机号已注册"，同样忽略
        pass
    data = call("POST", "%s/auth/login" % base_url,
                json={"phone": phone, "password": password})
    return data["accessToken"]


# ---------------------------------------------------------------- 主流程

def main():
    ap = argparse.ArgumentParser(description="生成 JMeter 压测所需的 CSV 数据文件")
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", default="8085")
    ap.add_argument("--users", type=int, default=60, help="生成的测试账号数量（需 >= 最大线程数）")
    ap.add_argument("--seats-per-order", type=int, default=2, help="每单座位数")
    ap.add_argument("--schedule-id", default=None, help="指定场次 ID，不填则自动挑选")
    ap.add_argument("--rows", type=int, default=40, help="order-seats.csv 行数（每行一组独立座位）")
    ap.add_argument("--pay-rows", type=int, default=20, help="TG06 待支付订单数")
    ap.add_argument("--cancel-rows", type=int, default=20, help="TG07 待取消订单数")
    ap.add_argument("--refund-rows", type=int, default=10, help="TG08 已支付待退款订单数")
    ap.add_argument("--password", default=DEFAULT_PASSWORD)
    args = ap.parse_args()

    base_url = "http://%s:%s" % (args.host, args.port)

    # 健康检查
    try:
        call("GET", "%s/movie/now-showing?pageNo=1&pageSize=1" % base_url)
    except Exception as e:
        print("[错误] 无法连接后端 %s：%s" % (base_url, e))
        return 1
    print("[1/4] 后端 %s 可用" % base_url)

    schedule_id, movie_id = pick_schedule(base_url, args.schedule_id)
    print("[2/4] 选定场次 scheduleId=%s movieId=%s" % (schedule_id, movie_id))

    sm, available = fetch_available_seats(base_url, schedule_id)
    need = args.rows * args.seats_per_order + 1
    if len(available) < need:
        print("[错误] 可售座位不足：需要 %d，实际 %d" % (need, len(available)))
        return 1
    print("      场次 %s《%s》%s 可售座位 %d 个（需要 %d）"
          % (schedule_id, sm.get("movieName"), sm.get("startTime"), len(available), need))

    # ---- 测试账号与 token ----
    print("[3/4] 准备 %d 个测试账号..." % args.users)
    tokens = []
    for i in range(args.users):
        phone = str(PHONE_BASE + i)
        token = ensure_user(base_url, phone, args.password)
        tokens.append((phone, token))
        if (i + 1) % 10 == 0:
            print("      已就绪 %d/%d" % (i + 1, args.users))
        time.sleep(0.05)

    # ---- 写 CSV ----
    print("[4/7] 写入 CSV 文件...")
    with open(os.path.join(BENCH_DIR, "tokens.csv"), "w", encoding="utf-8", newline="") as f:
        f.write("phone;token\n")
        for phone, token in tokens:
            f.write("%s;%s\n" % (phone, token))

    with open(os.path.join(BENCH_DIR, "targets.csv"), "w", encoding="utf-8", newline="") as f:
        f.write("scheduleId;movieId\n")
        f.write("%s;%s\n" % (schedule_id, movie_id))

    with open(os.path.join(BENCH_DIR, "order-seats.csv"), "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, delimiter=";", quoting=csv.QUOTE_MINIMAL)
        writer.writerow(["scheduleId", "seats"])
        cursor = 0
        for _ in range(args.rows):
            group = available[cursor:cursor + args.seats_per_order]
            cursor += args.seats_per_order
            writer.writerow([schedule_id, seat_positions_json(group)])

    # 超卖验证：所有线程抢**同一个座位**，正常期望只有 1 个线程成功
    hot_seat = available[cursor]
    cursor += 1
    with open(os.path.join(BENCH_DIR, "oversell-seats.csv"), "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, delimiter=";", quoting=csv.QUOTE_MINIMAL)
        writer.writerow(["scheduleId", "seats"])
        writer.writerow([schedule_id, seat_positions_json([hot_seat])])

    # ---- TG06/TG07/TG08 订单类场景所需订单 ----
    # 每个订单用独立的账号 + 独立的座位，避免用户级限流（10 秒 3 次）干扰。
    print("[5/7] 准备订单类场景数据（支付 / 取消 / 退款）...")
    need_orders = args.pay_rows + args.cancel_rows + args.refund_rows
    if len(available) < cursor + need_orders:
        print("[错误] 可售座位不足：还需要 %d 个" % (len(available) - cursor))
        return 1

    def create_order(idx, token):
        """用第 idx 个可用座位创建一个待支付订单，返回 orderId"""
        pos = available[cursor + idx]
        data = call("POST", "%s/order" % base_url,
                    json={"scheduleId": int(schedule_id),
                          "seats": [{"rowNo": pos[0], "colNo": pos[1]}]},
                    headers={"Authorization": "Bearer " + token})
        return str(data["id"]), pos

    pay_rows, cancel_rows, refund_rows = [], [], []
    seat_cursor = 0
    order_no = 0
    for kind, bucket, count, need_pay in (
            ("pay", pay_rows, args.pay_rows, False),
            ("cancel", cancel_rows, args.cancel_rows, False),
            ("refund", refund_rows, args.refund_rows, True)):
        for _ in range(count):
            phone, token = tokens[order_no % len(tokens)]
            order_no += 1
            try:
                oid, seat = create_order(seat_cursor, token)
                seat_cursor += 1
            except Exception as e:
                print("      跳过一个订单（%s）：%s" % (kind, e))
                continue
            if need_pay:
                # 退款场景需要订单已是「已支付」状态
                try:
                    call("POST", "%s/order/%s/pay" % (base_url, oid),
                         headers={"Authorization": "Bearer " + token})
                except Exception as e:
                    print("      支付失败，跳过该订单：%s" % e)
                    continue
            bucket.append((oid, token))
        print("      %s 订单 %d 个" % (kind, len(bucket)))

    with open(os.path.join(BENCH_DIR, "pay-orders.csv"), "w", encoding="utf-8", newline="") as f:
        f.write("orderId;token\n")
        for oid, token in pay_rows:
            f.write("%s;%s\n" % (oid, token))

    with open(os.path.join(BENCH_DIR, "cancel-orders.csv"), "w", encoding="utf-8", newline="") as f:
        f.write("orderId;token\n")
        for oid, token in cancel_rows:
            f.write("%s;%s\n" % (oid, token))

    with open(os.path.join(BENCH_DIR, "refund-orders.csv"), "w", encoding="utf-8", newline="") as f:
        f.write("orderId;token\n")
        for oid, token in refund_rows:
            f.write("%s;%s\n" % (oid, token))

    # ---- TG09 登录压测账号 ----
    print("[6/7] 写入登录压测账号...")
    with open(os.path.join(BENCH_DIR, "login-users.csv"), "w", encoding="utf-8", newline="") as f:
        f.write("phone;password\n")
        for phone, _ in tokens:
            f.write("%s;%s\n" % (phone, args.password))

    print("[7/7] 完成，生成于 %s：" % BENCH_DIR)
    for name in ("targets.csv", "order-seats.csv", "oversell-seats.csv", "tokens.csv",
                 "pay-orders.csv", "cancel-orders.csv", "refund-orders.csv", "login-users.csv"):
        print("  - %s" % name)
    print("\n下一步：执行 run-bench.ps1 或")
    print("  jmeter -n -t movie-bench.jmx -Jhost=%s -Jport=%s -l results.jtl -e -o report"
          % (args.host, args.port))
    print("\n注意：TG05 会真实锁住座位 %d排%d列（15 分钟超时自动释放），TG02 占用 %d 组座位。"
          % (hot_seat[0], hot_seat[1], args.rows))
    print("      TG06/TG07/TG08 会真实创建 %d 个订单。"
          % (len(pay_rows) + len(cancel_rows) + len(refund_rows)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
