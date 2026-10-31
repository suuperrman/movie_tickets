package org.javaup.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户端：选座页面返回的座位图（优化版：Bitmap 压缩 + 扁平 ID 数组）
 *
 * 对应接口：GET /schedule/{scheduleId}/seats
 *
 * 座位编码规则：
 *   - seatIds：按 rowNo×colCount + colNo 索引的扁平数组（雪花 ID 字符串，防 JS 精度丢失）
 *   - seatStatusBitmap：2 bit / 座（00=可售 01=锁定 10=已售），大端序，Base64 编码
 *     索引 = rowNo × colCount + colNo（从 0 开始）
 *     前端解码：(byte[idx/4] >> ((3 - idx%4) * 2)) & 0x03 → status
 *
 * 示例（15×20 影厅 = 300 座）：
 *   - 旧格式：List<SeatItem> 300 条 JSON ≈ 24KB
 *   - 新格式：seatIds ≈ 300×20B=6KB + bitmap(300×2bit=75B) ≈ 3KB gzip 后
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapVO {

    // ========== 场次基础信息 ==========

    private Long scheduleId;
    private Long movieId;
    private String movieName;
    private String posterUrl;
    private Long cinemaId;
    private String cinemaName;
    private Long hallId;
    private String hallName;
    /** 1=普通 2=IMAX 3=情侣厅 */
    private Integer hallType;
    private String startTime;
    private String endTime;
    private BigDecimal price;

    // ========== 影厅布局 ==========

    private Integer rowCount;
    private Integer colCount;

    // ========== 压缩座位数据 ==========

    /**
     * 座位 ID 数组（按 rowNo×colCount + colNo 索引）
     * 用 String 是因为雪花 ID 超过 JS MAX_SAFE_INTEGER
     */
    private List<String> seatIds;

    /**
     * 座位状态 Bitmap（Base64 编码）
     * 每个座位 2 bit：00=可售(0) 01=锁定(1) 10=已售(2)
     */
    private String seatStatusBitmap;
}
