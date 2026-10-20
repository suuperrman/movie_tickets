package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单明细表 order_item
 */
@TableName(value = "order_item")
@Data
public class OrderItem implements Serializable {
    @TableId  // 默认 ASSIGN_ID（雪花ID），由应用层生成
    private Long id;
    private Long orderId;
    private Long scheduleSeatId;
    private String seatCode;
    private Integer rowNo;
    private Integer colNo;
    private BigDecimal price;
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
