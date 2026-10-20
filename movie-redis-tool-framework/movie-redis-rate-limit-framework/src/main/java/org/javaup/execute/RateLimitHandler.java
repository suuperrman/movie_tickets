package org.javaup.execute;
import org.javaup.ratelimit.extension.RateLimitScene;

/**
 * @program: movie-ticket
 * @description: 限流执行 接口
 * @author: xiong
 **/
public interface RateLimitHandler {
   
    void execute(Long voucherId, Long userId, RateLimitScene scene);
}
