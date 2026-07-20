package com.heartlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.dto.SubmitReviewDTO;
import com.heartlink.entity.Review;

public interface ReviewService extends IService<Review> {
    Review submitReview(SubmitReviewDTO dto, Long userId);
    IPage<Review> getProductReviews(Long productId, Integer page, Integer size);
    IPage<Review> getMyReviews(Long userId, Integer page, Integer size);
}
