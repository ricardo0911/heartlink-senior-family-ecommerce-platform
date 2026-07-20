package com.heartlink.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.Supplier;
import com.heartlink.mapper.SupplierMapper;
import com.heartlink.service.SupplierService;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {
}
