package com.heartlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heartlink.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
