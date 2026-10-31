package org.javaup.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.entity.SysUser;
import org.javaup.mapper.SysUserMapper;
import org.javaup.utils.MaskUtil;
import org.javaup.utils.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

/**
 * 管理端 - 用户管理接口
 *
 * 接口清单：
 *   GET    /admin/user/page              分页查询
 *   GET    /admin/user/{id}              详情
 *   PATCH  /admin/user/{id}/status       启用/禁用
 *   POST   /admin/user/{id}/reset-pwd    重置密码
 */
@Slf4j
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户
     */
    @GetMapping("/page")
    public Result<Page<SysUser>> page(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        // 只查普通用户（role=0），管理员账号不在用户管理里展示
        wrapper.eq(SysUser::getRole, 0);
        wrapper.orderByDesc(SysUser::getCreatedAt);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        // 脱敏：不返回密码，手机号脱敏
        page.getRecords().forEach(u -> {
            u.setPassword(null);
            u.setPhone(MaskUtil.maskPhone(u.getPhone()));
        });
        return Result.ok(page);
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
            user.setPhone(MaskUtil.maskPhone(user.getPhone()));
        }
        return Result.ok(user);
    }

    /**
     * 启用/禁用用户
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return Result.fail("状态不能为空");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        user.setUpdatedAt(new Date());
        sysUserMapper.updateById(user);
        log.info("[Admin] 用户状态更新 id={} status={}", id, status);
        return Result.ok();
    }

    /**
     * 重置密码（默认重置为 123456）
     */
    @PostMapping("/{id}/reset-pwd")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        String newPassword = (body != null && body.containsKey("password"))
                ? body.get("password") : "123456";
        String encoded = PasswordEncoder.encode(newPassword);

        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(encoded);
        user.setUpdatedAt(new Date());
        sysUserMapper.updateById(user);
        log.info("[Admin] 重置用户密码 id={}", id);
        return Result.ok();
    }
}
