package org.javaup.dto;

import lombok.Data;
/**
 * @program: movie-ticket
 * @description: 用户-入参
 * @author: xiong
 **/
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    /** 角色：0普通用户 1管理员 */
    private Integer role;
}
