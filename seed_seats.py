"""
批量生成 seat + schedule_seat 数据
- 为所有 hall 生成 seat（row_count × col_count）
- 为所有 schedule 生成 schedule_seat（用 seat 表记录）
"""
import pymysql
import time

# 简单雪花 ID 生成器（Python 版）
class SimpleSnowflake:
    def __init__(self):
        self.epoch = 1700000000000  # 起始时间戳
        self.worker_id = 1
        self.sequence = 0
        self.last_ts = -1

    def next_id(self):
        ts = int(time.time() * 1000) - self.epoch
        if ts < self.last_ts:
            ts = self.last_ts
        if ts == self.last_ts:
            self.sequence = (self.sequence + 1) & 4095
            if self.sequence == 0:
                ts = self._wait_next_ms(self.last_ts)
        else:
            self.sequence = 0
        self.last_ts = ts
        return ((ts << 22) | (self.worker_id << 12) | self.sequence)

    def _wait_next_ms(self, last):
        ts = int(time.time() * 1000) - self.epoch
        while ts <= last:
            ts = int(time.time() * 1000) - self.epoch
        return ts

sf = SimpleSnowflake()

def gen_snowflake():
    return sf.next_id()

conn = pymysql.connect(
    host='192.168.150.101', port=3306,
    user='root', password='123456',
    database='movie_ticket', charset='utf8mb4'
)
cur = conn.cursor()

# ==================== 1. 生成 seat 表数据 ====================
print("=== 1. 生成 seat 表数据 ===")
cur.execute("SELECT id, row_count, col_count FROM hall")
halls = cur.fetchall()
print(f"  hall 数量: {len(halls)}")

seat_count = 0
for hall_id, row_count, col_count in halls:
    for r in range(1, row_count + 1):
        for c in range(1, col_count + 1):
            row_letter = chr(ord('A') + r - 1)
            seat_code = f"{row_letter}{c}"
            seat_id = gen_snowflake()
            cur.execute(
                "INSERT INTO seat (id, hall_id, row_no, col_no, seat_code, is_active) "
                "VALUES (%s, %s, %s, %s, %s, 1)",
                (seat_id, hall_id, r, c, seat_code)
            )
            seat_count += 1

conn.commit()
print(f"  ✅ seat 记录总数: {seat_count}")

# ==================== 2. 生成 schedule_seat 表数据 ====================
print("\n=== 2. 生成 schedule_seat 表数据 ===")
cur.execute("""
    SELECT s.id, s.hall_id, s.price, h.row_count, h.col_count
    FROM schedule s
    JOIN hall h ON s.hall_id = h.id
""")
schedules = cur.fetchall()
print(f"  schedule 数量: {len(schedules)}")

ss_count = 0
for sched_id, hall_id, price, row_count, col_count in schedules:
    # 先查出这个 hall 刚插入的 seat 记录
    cur.execute(
        "SELECT id, row_no, col_no FROM seat WHERE hall_id = %s ORDER BY row_no, col_no",
        (hall_id,)
    )
    seats = cur.fetchall()
    if len(seats) != row_count * col_count:
        print(f"  ⚠️ hall {hall_id} seat 数量 {len(seats)} != {row_count}*{col_count}, 跳过 schedule {sched_id}")
        continue

    for seat_id, row_no, col_no in seats:
        ss_id = gen_snowflake()
        cur.execute(
            "INSERT INTO schedule_seat "
            "(id, schedule_id, seat_id, row_no, col_no, price, status, lock_user_id, version) "
            "VALUES (%s, %s, %s, %s, %s, %s, 0, 0, 0)",
            (ss_id, sched_id, seat_id, row_no, col_no, price)
        )
        ss_count += 1

conn.commit()
print(f"  ✅ schedule_seat 记录总数: {ss_count}")

# ==================== 3. 更新 schedule.total_seat ====================
print("\n=== 3. 更新 schedule.total_seat ===")
cur.execute("""
    UPDATE schedule s
    JOIN (
        SELECT schedule_id, COUNT(*) as cnt
        FROM schedule_seat
        GROUP BY schedule_id
    ) ss ON s.id = ss.schedule_id
    SET s.total_seat = ss.cnt,
        s.available_seat = ss.cnt
""")
print(f"  ✅ 已更新 schedule.total_seat")

conn.commit()

# ==================== 4. 最终验证 ====================
print("\n=== 4. 最终验证 ===")
cur.execute("SELECT COUNT(*) FROM seat")
print(f"  seat: {cur.fetchone()[0]}")
cur.execute("SELECT COUNT(*) FROM schedule_seat")
print(f"  schedule_seat: {cur.fetchone()[0]}")
cur.execute("SELECT id, row_no, col_no, seat_code FROM seat WHERE hall_id = 1743568293830000021 LIMIT 5")
print(f"  seat 样例 (hall_id=...021): {cur.fetchall()}")
cur.execute("SELECT id, row_no, col_no, status FROM schedule_seat WHERE schedule_id = 1743568293832000007 LIMIT 5")
print(f"  schedule_seat 样例 (schedule_id=...007): {cur.fetchall()}")

cur.close()
conn.close()
print("\n🎉 全部完成！")
