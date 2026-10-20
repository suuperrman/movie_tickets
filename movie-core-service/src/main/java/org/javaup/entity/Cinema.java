package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 影院表
 * @TableName cinema
 */
@TableName(value ="cinema")
@Data
public class Cinema implements Serializable {
    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 影院名称
     */
    private String name;

    /**
     * 所在城市
     */
    private String city;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 状态：1正常 0停用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}