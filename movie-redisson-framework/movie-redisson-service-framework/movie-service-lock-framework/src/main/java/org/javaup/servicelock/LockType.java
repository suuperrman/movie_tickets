package org.javaup.servicelock;

/**
 * @program: movie-ticket 
 * @description: 分布式锁 锁类型
 * @author: xiong
 **/
public enum LockType {
    /**
     * 锁类型
     */
    Reentrant,
    
    Fair,
   
    Read,
    
    Write;

    LockType() {
    }

}
