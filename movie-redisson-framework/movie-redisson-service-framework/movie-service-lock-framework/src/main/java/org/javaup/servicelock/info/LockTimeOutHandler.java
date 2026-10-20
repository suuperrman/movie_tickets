package org.javaup.servicelock.info;

/**
 * @program: movie-ticket 
 * @description: 处理失败抽象
 * @author: xiong
 **/
public interface LockTimeOutHandler {
    
    /**
     * 处理
     * @param lockName 锁名
     * */
    void handler(String lockName);
}
