package org.javaup.core;

/**
 * @program: movie-ticket 
 * @description: 延迟队列 消费者接口
 * @author: xiong
 **/
public interface ConsumerTask {
    
    void execute(String content);
  
    String topic();
}
