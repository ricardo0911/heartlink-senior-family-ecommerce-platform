package com.heartlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heartlink.entity.PaymentRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代付请求Mapper
 */
@Mapper
public interface PaymentRequestMapper extends BaseMapper<PaymentRequest> {
}
