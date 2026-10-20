package org.javaup.dto.cinema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 影院列表项 / 详情 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaVO {
    private Long id;
    private String name;
    private String city;
    private String address;
    private String phone;
    /** 1=正常 0=停用 */
    private Integer status;
    /** 影厅数量 */
    private Integer hallCount;
    private String createdAt;
}
