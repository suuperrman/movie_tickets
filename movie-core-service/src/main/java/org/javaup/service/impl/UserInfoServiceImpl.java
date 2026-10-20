package org.javaup.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.javaup.mapper.UserInfoMapper;
import org.javaup.model.UserInfo;
import org.javaup.service.IUserInfoService;
import org.springframework.stereotype.Service;

/**
 * 用户信息服务实现
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
        implements IUserInfoService {
}
