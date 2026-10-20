package org.javaup.dto.movie;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 新增/修改电影请求 DTO
 */
@Data
public class MovieSaveDTO {

    @NotBlank(message = "电影名称不能为空")
    @Size(max = 100, message = "电影名称最长 100")
    private String name;

    @NotBlank(message = "导演不能为空")
    @Size(max = 100, message = "导演最长 100")
    private String director;

    @NotNull(message = "时长不能为空")
    @Min(value = 1, message = "时长至少 1 分钟")
    private Integer duration;

    @NotBlank(message = "上映日期不能为空")
    private String releaseDate;

    @NotBlank(message = "海报 URL 不能为空")
    @Size(max = 255, message = "海报 URL 最长 255")
    private String posterUrl;

    private String description;

    private Integer status;

    private List<Long> categoryIds;

    @Valid
    private List<ActorItem> actors;

    @Data
    public static class ActorItem {
        /** 演员已有 ID；若传了就不新建 */
        private Long actorId;
        /** 演员姓名（新增时必填） */
        private String name;
        /** 演员头像（新增时必填） */
        private String avatarUrl;
        /** 性别 0=未知 1=男 2=女 */
        private Integer gender;
        /** 演员简介 */
        private String intro;
        /** 在本电影中饰演的角色 */
        private String roleName;
        /** 1=主演 0=配角 */
        private Integer isLead;
        /** 排序 */
        private Integer sort;
    }
}
