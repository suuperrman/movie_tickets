package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀优惠券
 */
@TableName("seckill_voucher")
@Data
public class SeckillVoucher implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 优惠券ID */
    private Long voucherId;

    /** 初始库存 */
    private Integer initStock;

    /** 剩余库存 */
    private Integer stock;

    /** 允许的会员等级，逗号分隔 */
    private String allowedLevels;

    /** 最低会员等级 */
    private Integer minLevel;

    /** 所属店铺ID */
    private Long shopId;

    /** 开抢时间 */
    private LocalDateTime beginTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：1上架 0下架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
