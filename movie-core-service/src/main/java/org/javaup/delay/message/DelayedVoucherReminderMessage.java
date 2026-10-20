package org.javaup.delay.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @program: movie-ticket
 * @description: 开抢提醒消息DTO
 * @author: xiong
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DelayedVoucherReminderMessage {
    
    private Long voucherId;
    
    private LocalDateTime beginTime;
}