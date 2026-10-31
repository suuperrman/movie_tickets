package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 电影评价表
 * @TableName movie_review
 */
@TableName(value = "movie_review")
@Data
public class MovieReview implements Serializable {

    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 电影ID，关联 movie.id
     */
    private Long movieId;

    /**
     * 评价用户ID，关联 sys_user.id
     */
    private Long userId;

    /**
     * 关联订单ID，关联 order.id
     */
    private Long orderId;

    /**
     * 评分 1-5 星
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 审核状态：0待审核 1已通过 2已拒绝
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
