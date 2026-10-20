package org.javaup.ratelimit.extension;

import org.javaup.enums.BaseCode;

/**
 * @program: movie-ticket
 * @description: 默认空实现
 * @author: xiong
 **/
public class NoOpRateLimitPenaltyPolicy implements RateLimitPenaltyPolicy {
    @Override
    public void apply(RateLimitContext ctx, BaseCode reason) {
        // no-op
    }
}