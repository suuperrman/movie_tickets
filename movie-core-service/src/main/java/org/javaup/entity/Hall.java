package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 影厅表
 * @TableName hall
 */
@TableName(value ="hall")
@Data
public class Hall implements Serializable {
    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 影院ID，关联 cinema.id
     */
    private Long cinemaId;

    /**
     * 影厅名，如：1号厅
     */
    private String name;

    /**
     * 影厅类型：1普通 2IMAX 3情侣厅
     */
    private Integer type;

    /**
     * 座位行数
     */
    private Integer rowCount;

    /**
     * 座位列数
     */
    private Integer colCount;

    /**
     * 座位布局：过道、情侣座、不可用座位
     */
    private Object layout;

    /**
     * 创建时间
     */
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}