package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 电影-演员关联表 movie_actor_rel
 */
@TableName(value = "movie_actor_rel")
@Data
public class MovieActorRel implements Serializable {
    @TableId
    private Long id;
    private Long movieId;
    private Long actorId;
    private String roleName;
    private Integer sort;
    /** 1=主演 0=配角 */
    private Integer isLead;
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
