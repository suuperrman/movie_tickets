package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 分类表 movie_category
 */
@TableName(value = "movie_category")
@Data
public class MovieCategory implements Serializable {
    @TableId
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
    private Date createdAt;
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
