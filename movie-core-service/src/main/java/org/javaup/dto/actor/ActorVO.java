package org.javaup.dto.actor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 演员列表项 / 详情 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorVO {
    private Long id;
    private String name;
    private String avatarUrl;
    /** 0=未知 1=男 2=女 */
    private Integer gender;
    private String intro;
    /** 1=启用 0=停用 */
    private Integer status;
    /** 参演电影数量（管理端详情附带） */
    private Integer movieCount;
    private String createdAt;
}
