package org.javaup.dto.movie;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户端：选座页面返回的座位图（Bitmap 压缩，不含座位 ID 数组）
 *
 * 对应接口：GET /schedule/{scheduleId}/seats
 *
 * 只保留前端实际使用的字段：影片名、影院名、影厅名、开场时间、票价、行列数、状态位图。
 *
 * 座位编码规则：
 *   - seatStatusBitmap：2 bit / 座（00=可售 01=锁定 10=已售 11=无此座位），大端序，Base64 编码
 *     索引 = (rowNo-1) × colCount + (colNo-1)
 *     前端解码：(byte[idx/4] >> ((3 - idx%4) * 2)) & 0x03 → status
 *   - 下单不再传座位 ID，而是传 {scheduleId, seats:[{rowNo,colNo}]}，服务端按场次+行列解析
 *
 * seatIds 只保留在服务端缓存里，用于把 Redis HASH 的实时状态覆盖回 Bitmap；
 * 响应前会被置空，客户端拿不到这个数组。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapVO {

    // ========== 场次基础信息（前端展示用） ==========

    private String movieName;
    private String cinemaName;
    private String hallName;
    private String startTime;
    private BigDecimal price;

    // ========== 影厅布局 ==========

    private Integer rowCount;
    private Integer colCount;

    // ========== 压缩座位数据 ==========

    /**
     * 座位 ID 数组（按行×列索引），仅服务端使用
     * 注意：fastjson 2.x 也遵守 Jackson 的 @JsonIgnore，写 Redis 缓存时会丢掉该字段，
     * 所以缓存里由 SeatMapCacheVO 单独保存一份 seatIds；响应前这里置空
     * 用 String 是因为雪花 ID 超过 JS MAX_SAFE_INTEGER
     */
    @JsonIgnore
    private List<String> seatIds;

    /**
     * 座位状态 Bitmap（Base64 编码）
     * 每个座位 2 bit：00=可售(0) 01=锁定(1) 10=已售(2) 11=无此座位(3)
     */
    private String seatStatusBitmap;
}
