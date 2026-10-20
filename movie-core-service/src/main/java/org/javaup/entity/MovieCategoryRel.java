package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 电影-分类关联表 movie_category_rel
 */
@TableName(value = "movie_category_rel")
@Data
public class MovieCategoryRel implements Serializable {
    @TableId
    private Long id;
    private Long movieId;
    private Long categoryId;
    private Integer sort;
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
