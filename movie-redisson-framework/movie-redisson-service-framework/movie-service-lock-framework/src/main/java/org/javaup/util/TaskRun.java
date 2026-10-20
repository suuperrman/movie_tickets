package org.javaup.util;

/**
 * @program: movie-ticket 
 * @description: 分布式锁 方法类型执行 无返回值的业务
 * @author: xiong
 **/
@FunctionalInterface
public interface TaskRun {
    
    /**
     * 执行任务
     * */
    void run();
}
