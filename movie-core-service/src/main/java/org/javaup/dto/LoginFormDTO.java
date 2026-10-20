package org.javaup.dto;

import lombok.Data;

/**
 * @program: movie-ticket
 * @description: 登录-入参
 * @author: xiong
 **/
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
