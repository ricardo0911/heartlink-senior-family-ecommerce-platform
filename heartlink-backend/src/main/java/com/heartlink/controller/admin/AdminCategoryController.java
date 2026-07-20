package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heartlink.common.Result;
import com.heartlink.entity.Category;
import com.heartlink.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端-分类管理")
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "获取分类列表")
    @GetMapping("/list")
    public Result<List<Category>> list() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSort);
        return Result.success(categoryService.list(wrapper));
    }

    @Operation(summary = "新增或更新分类")
    @PostMapping("/save")
    public Result<Category> save(@RequestBody Category category) {
        if (category.getId() != null) {
            categoryService.updateById(category);
        } else {
            categoryService.save(category);
        }
        return Result.success(category);
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.removeById(id);
        return Result.success(null);
    }
}
