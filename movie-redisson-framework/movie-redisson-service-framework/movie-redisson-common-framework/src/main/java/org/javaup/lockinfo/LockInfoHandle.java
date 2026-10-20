package org.javaup.lockinfo;

import org.aspectj.lang.JoinPoint;

/**
 * @program: movie-ticket 
 * @description: 锁信息抽象
 * @author: xiong
 **/
public interface LockInfoHandle {
   
    String getLockName(JoinPoint joinPoint, String name, String[] keys);
    
    String simpleGetLockName(String name,String[] keys);
}
