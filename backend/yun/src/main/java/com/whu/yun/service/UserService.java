package com.whu.yun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.yun.entity.User;
import com.whu.yun.entity.vo.SystemUserInfoVo;

import java.util.List;
import java.util.Map;


/**
 * (User)表服务接口
 *
 * @author makejava
 * @since 2025-02-24 20:37:39
 */
public interface UserService extends IService<User> {
    //注册
    boolean register(User user);

    //登录
    String login(String username, String password);

}
