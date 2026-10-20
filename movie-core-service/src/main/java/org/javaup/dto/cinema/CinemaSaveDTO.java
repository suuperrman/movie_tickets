package org.javaup.dto.cinema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/修改影院请求 DTO
 */
@Data
public class CinemaSaveDTO {

    @NotBlank(message = "影院名称不能为空")
    @Size(max = 100, message = "名称最长 100")
    private String name;

    @NotBlank(message = "城市不能为空")
    @Size(max = 50, message = "城市最长 50")
    private String city;

    @NotBlank(message = "地址不能为空")
    @Size(max = 255, message = "地址最长 255")
    private String address;

    @NotBlank(message = "电话不能为空")
    @Size(max = 20, message = "电话最长 20")
    private String phone;

    /** 1=正常 0=停用 */
    private Integer status;
}
