package com.heartlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heartlink.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
