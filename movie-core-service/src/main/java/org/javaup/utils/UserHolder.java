package org.javaup.utils;

import org.javaup.dto.UserDTO;

/**
 * @program: movie-ticket
 * @description: 用户持有器-movie-ticket版本使用
 * @author: xiong
 **/
public class UserHolder {
    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        TL.set(user);
    }

    public static UserDTO getUser(){
        return TL.get();
    }

    public static void removeUser(){
        TL.remove();
    }
}
