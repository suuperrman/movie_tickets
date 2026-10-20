package org.javaup.dto;

import lombok.Data;

import java.util.List;

/**
 * @program: movie-ticket
 * @description: 滚动-结果
 * @author: xiong
 **/
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
