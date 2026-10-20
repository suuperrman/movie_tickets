package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 演员/影人表
 * @TableName actor
 */
@TableName(value ="actor")
@Data
public class Actor implements Serializable {
    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 演员姓名
     */
    private String name;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 性别：0未知 1男 2女
     */
    private Integer gender;

    /**
     * 简介
     */
    private String intro;

    /**
     * 状态：1启用 0停用
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