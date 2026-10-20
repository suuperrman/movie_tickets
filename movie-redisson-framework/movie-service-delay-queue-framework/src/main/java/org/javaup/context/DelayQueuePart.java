package org.javaup.context;

import org.javaup.core.ConsumerTask;
import lombok.Data;

/**
 * @program: movie-ticket 
 * @description: 消息主题
 * @author: xiong
 **/
@Data
public class DelayQueuePart {
    
    private final DelayQueueBasePart delayQueueBasePart;
 
    private final ConsumerTask consumerTask;
    
    public DelayQueuePart(DelayQueueBasePart delayQueueBasePart, ConsumerTask consumerTask){
        this.delayQueueBasePart = delayQueueBasePart;
        this.consumerTask = consumerTask;
    }
}
