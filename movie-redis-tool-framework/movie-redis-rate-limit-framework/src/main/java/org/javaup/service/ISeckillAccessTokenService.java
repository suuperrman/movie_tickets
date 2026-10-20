package org.javaup.service;

/**
 * @program: movie-ticket
 * @description: 令牌
 * @author: xiong
 **/
public interface ISeckillAccessTokenService {
  
    boolean isEnabled();
 
    String issueAccessToken(Long voucherId, Long userId);
    
    boolean validateAndConsume(Long voucherId, Long userId, String token);
}