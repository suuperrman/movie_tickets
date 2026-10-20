package org.javaup.dto.actor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/修改演员请求 DTO
 */
@Data
public class ActorSaveDTO {

    @NotBlank(message = "演员姓名不能为空")
    @Size(max = 100, message = "姓名最长 100")
    private String name;

    @NotBlank(message = "头像 URL 不能为空")
    @Size(max = 255, message = "头像 URL 最长 255")
    private String avatarUrl;

    /** 0=未知 1=男 2=女 */
    private Integer gender;

    /** 简介，最长 1000 */
    @Size(max = 1000, message = "简介最长 1000")
    private String intro;

    /** 1=启用 0=停用 */
    private Integer status;
}
