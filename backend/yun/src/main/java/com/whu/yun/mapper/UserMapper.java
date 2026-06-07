package com.whu.yun.mapper;

import com.whu.yun.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户实体
     */
    User findByUsername(@Param("username") String username);

    /**
     * 插入一个新用户
     * @param user 用户实体
     * @return 影响的行数
     */
    int insertUser(User user);
}

