package org.javaup.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @program: movie-ticket
 * @description: redis数据
 * @author: xiong
 **/
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
