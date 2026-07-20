package com.heartlink.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.Category;
import com.heartlink.mapper.CategoryMapper;
import com.heartlink.service.CategoryService;
import org.springframework.stereotype.Service;

/**
 * 分类服务实现
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
}
