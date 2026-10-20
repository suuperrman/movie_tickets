package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 影片表
 * @TableName movie
 */
@TableName(value ="movie")
@Data
public class Movie implements Serializable {
    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 影片名称
     */
    private String name;

    /**
     * 导演
     */
    private String director;

    /**
     * 时长(分钟)
     */
    private Integer duration;

    /**
     * 上映日期
     */
    private Date releaseDate;

    /**
     * 海报URL
     */
    private String posterUrl;

    /**
     * 影片简介
     */
    private String description;

    /**
     * 状态：1上映 0下架
     */
    private Integer status;

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