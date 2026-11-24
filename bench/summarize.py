#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JMeter 结果汇总：从 result.jtl 计算简历可用的量化指标
=====================================================
  python summarize.py results/result.jtl

输出每个采样器的：请求数 / 错误率 / 平均延迟 / P95 / P99 / TPS
以及 TG05 超卖验证的结论（成功单数 vs 座位数）。
"""

import csv
import os
import sys
from collections import defaultdict


def percentile(values, p):
    if not values:
        return 0
    values = sorted(values)
    idx = int(round((p / 100.0) * len(values))) - 1
    idx = max(0, min(idx, len(values) - 1))
    return values[idx]


def main():
    if len(sys.argv) < 2:
        print("用法: python summarize.py <result.jtl> [oversell-seats.csv]")
        return 1
    jtl = sys.argv[1]
    if not os.path.exists(jtl):
        print("[错误] 找不到 %s" % jtl)
        return 1

    stats = defaultdict(lambda: {"n": 0, "err": 0, "rejected": 0, "lat": [], "first": None, "last": None})
    with open(jtl, "r", encoding="utf-8", errors="ignore") as f:
        reader = csv.DictReader(f)
        for row in reader:
            label = row.get("label") or row.get("Label") or "?"
            s = stats[label]
            s["n"] += 1
            failed = (row.get("success") or "").strip().lower() not in ("true", "1")
            if failed:
                s["err"] += 1
                # HTTP 200 + 业务断言失败 =「预期内的业务拒绝」（如座位已被占用），不算系统错误；
                # HTTP 非 200 / 连接失败才算真正的错误。
                code = (row.get("responseCode") or "").strip()
                msg = row.get("failureMessage") or ""
                if code == "200" and "expected to contain" in msg:
                    s["rejected"] += 1
            try:
                elapsed = int(row.get("elapsed") or 0)
            except ValueError:
                elapsed = 0
            s["lat"].append(elapsed)
            try:
                ts = int(row.get("timeStamp") or 0)
            except ValueError:
                ts = 0
            if s["first"] is None or ts < s["first"]:
                s["first"] = ts
            if s["last"] is None or ts > s["last"]:
                s["last"] = ts

    print("=" * 78)
    print("JMeter 压测结果汇总   (%s)" % jtl)
    print("=" * 78)
    print("%-22s %8s %8s %8s %10s %8s %8s %8s" %
          ("采样器", "请求数", "真实错误", "业务拒绝", "平均(ms)", "P95", "P99", "TPS"))
    print("-" * 78)

    for label in sorted(stats):
        s = stats[label]
        lat = s["lat"]
        avg = sum(lat) / len(lat) if lat else 0
        real_err = s["err"] - s["rejected"]
        err_rate = real_err * 100.0 / s["n"] if s["n"] else 0
        duration = ((s["last"] - s["first"]) / 1000.0) if (s["first"] and s["last"]) else 0
        tps = (s["n"] / duration) if duration > 0.01 else 0
        print("%-22s %8d %7.2f%% %8d %10.0f %8d %8d %8.1f" %
              (label[:22], s["n"], err_rate, s["rejected"], avg,
               percentile(lat, 95), percentile(lat, 99), tps))

    print("-" * 78)
    print("说明：「业务拒绝」= HTTP 200 但业务逻辑拒绝（如座位已被占用），属预期行为；")
    print("      「真实错误」= HTTP 非 200 / 连接失败，才计入错误率。")

    # TG05 超卖验证
    for label, s in stats.items():
        if "OVERSELL" in label:
            ok = s["n"] - s["err"]
            print("-" * 78)
            print("【超卖验证】%d 个线程抢同一个座位，成功下单 %d 单，被正确拒绝 %d 次"
                  % (s["n"], ok, s["rejected"]))
            if len(sys.argv) > 2 and os.path.exists(sys.argv[2]):
                with open(sys.argv[2], encoding="utf-8") as f:
                    rows = [l for l in f.read().splitlines() if l.strip()]
                seats = rows[-1].split(";")[1].split(",")
                print("【超卖验证】目标座位共 %d 个（%s）" %
                      (len(seats), ",".join(seats)))
                if ok <= len(seats):
                    print("【超卖验证】✔ 无超卖：成功订单数 %d ≤ 座位数 %d" % (ok, len(seats)))
                else:
                    print("【超卖验证】✘ 存在超卖：成功订单数 %d > 座位数 %d" % (ok, len(seats)))
            break
    print("=" * 78)
    return 0


if __name__ == "__main__":
    sys.exit(main())
