package org.javaup.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.javaup.entity.SysUser;
import org.javaup.service.SysUserService;
import org.javaup.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

/**
* @author xiao
* @description 针对表【sys_user(系统用户表)】的数据库操作Service实现
* @createDate 2026-10-19 22:48:13
*/
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
    implements SysUserService{

}




