package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统用户表
 *
 * 对应数据库 movie_ticket.sys_user 表，列结构如下：
 *   id           bigint       主键
 *   phone        varchar(20)  手机号（唯一，作为登录用户名）
 *   password     varchar(100) 密码（bcrypt 加密）
 *   nickname     varchar(50)  昵称
 *   avatar       varchar(255) 头像
 *   status       tinyint      状态：1正常 0禁用
 *   created_at   datetime     创建时间
 *   updated_at   datetime     更新时间
 */
@TableName(value = "sys_user")
@Data
public class SysUser implements Serializable {

    /** 主键 */
    @TableId
    private Long id;

    /** 手机号（唯一，同时作为登录用户名） */
    private String phone;

    /** bcrypt 加密后的密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 状态：1正常 0禁用 */
    private Integer status;

    /** 角色：0普通用户 1管理员 */
    private Integer role;

    /** 创建时间 */
    private Date createdAt;

    /** 更新时间 */
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
