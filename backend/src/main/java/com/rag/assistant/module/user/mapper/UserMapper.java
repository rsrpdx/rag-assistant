package com.rag.assistant.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.assistant.module.user.model.entity.User;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM user WHERE username = #{username} AND is_deleted = 0")
    User selectByUsername(String username);

    @Select("SELECT * FROM user WHERE email = #{email} AND is_deleted = 0")
    User selectByEmail(String email);
}
