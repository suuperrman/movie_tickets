package org.javaup.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 电影评价 VO（用户端列表 / 我的评价 / 管理端列表共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO {

    private Long id;

    private Long movieId;

    /** 电影名称（批量补全，避免前端二次请求） */
    private String movieName;

    private Long userId;

    /** 评价用户昵称 */
    private String nickname;

    /** 评价用户头像 */
    private String avatar;

    /** 评分 1-5 星 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 审核状态：0待审核 1已通过 2已拒绝 */
    private Integer status;

    /** 创建时间 yyyy-MM-dd HH:mm */
    private String createdAt;
}
