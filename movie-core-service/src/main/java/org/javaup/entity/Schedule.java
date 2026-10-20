package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 场次表
 * @TableName schedule
 */
@TableName(value ="schedule")
@Data
public class Schedule implements Serializable {
    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 影片ID，关联 movie.id
     */
    private Long movieId;

    /**
     * 影院ID，关联 cinema.id
     */
    private Long cinemaId;

    /**
     * 影厅ID，关联 hall.id
     */
    private Long hallId;

    /**
     * 开场时间
     */
    private Date startTime;

    /**
     * 散场时间
     */
    private Date endTime;

    /**
     * 基础票价
     */
    private BigDecimal price;

    /**
     * 总座位数
     */
    private Integer totalSeat;

    /**
     * 剩余可售座位数(冗余，实际以 Redis 为准)
     */
    private Integer availableSeat;

    /**
     * 场次状态：1可售 2售罄 3已结束 0取消
     */
    private Integer status;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}