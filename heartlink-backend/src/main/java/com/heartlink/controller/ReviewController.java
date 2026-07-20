package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heartlink.common.Result;
import com.heartlink.dto.SubmitReviewDTO;
import com.heartlink.entity.Review;
import com.heartlink.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "商品评价")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "提交评价")
    @PostMapping("/submit")
    public Result<Review> submit(@RequestBody SubmitReviewDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        Review review = reviewService.submitReview(dto, userId);
        return Result.success("评价成功", review);
    }

    @Operation(summary = "商品评价列表")
    @GetMapping("/product/{productId}")
    public Result<IPage<Review>> productReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(reviewService.getProductReviews(productId, page, size));
    }

    @Operation(summary = "我的评价")
    @GetMapping("/my")
    public Result<IPage<Review>> myReviews(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(reviewService.getMyReviews(userId, page, size));
    }
}
