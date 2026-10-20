package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 影厅物理座位表
 * @TableName seat
 */
@TableName(value ="seat")
@Data
public class Seat implements Serializable {
    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 影厅ID，关联 hall.id
     */
    private Long hallId;

    /**
     * 座位行号
     */
    private Integer rowNo;

    /**
     * 座位列号
     */
    private Integer colNo;

    /**
     * 座位编码，如 5排8座 -> 5-8
     */
    private String seatCode;

    /**
     * 是否启用：1启用 0停用
     */
    private Integer isActive;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}