package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 场次-座位表 schedule_seat
 *
 * 物理座位（seat） × 场次（schedule）= 一张场次下的座位状态
 * status: 0=可售 1=锁定 2=已售
 */
@TableName(value = "schedule_seat")
@Data
public class ScheduleSeat implements Serializable {
    @TableId
    private Long id;
    private Long scheduleId;
    private Long seatId;
    private Integer rowNo;
    private Integer colNo;
    private BigDecimal price;
    /** 0=可售 1=锁定 2=已售 */
    private Integer status;
    private Long orderId;
    private Long lockUserId;
    private Date lockExpireAt;
    private Integer version;
    private Date updatedAt;

    @TableField(exist = false)
    private String seatCode;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
