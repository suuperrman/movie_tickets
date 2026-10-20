package org.javaup.lua;

import lombok.Data;

/**
 * @program: movie-ticket
 * @description: lua秒杀返回数据
 * @author: xiong
 **/
@Data
public class SeckillVoucherDomain {

    private Integer code;
    
    private Integer beforeQty;
    
    private Integer deductQty;
    
    private Integer afterQty;

}
