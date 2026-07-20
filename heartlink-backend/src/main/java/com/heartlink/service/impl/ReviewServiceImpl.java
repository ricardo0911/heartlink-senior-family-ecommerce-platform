package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.dto.SubmitReviewDTO;
import com.heartlink.entity.Review;
import com.heartlink.entity.User;
import com.heartlink.mapper.ReviewMapper;
import com.heartlink.service.ReviewService;
import com.heartlink.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    private final UserService userService;

    @Override
    public Review submitReview(SubmitReviewDTO dto, Long userId) {
        // Check duplicate
        long existing = count(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, dto.getOrderId())
                .eq(Review::getUserId, userId));
        if (existing > 0) {
            throw new RuntimeException("该订单已评价");
        }

        Review review = new Review();
        review.setProductId(dto.getProductId());
        review.setUserId(userId);
        review.setOrderId(dto.getOrderId());
        review.setContent(dto.getContent());
        review.setVoiceUrl(dto.getVoiceUrl());
        review.setRating(dto.getRating() != null ? dto.getRating() : 5);
        review.setImages(dto.getImages());
        save(review);
        return review;
    }

    @Override
    public IPage<Review> getProductReviews(Long productId, Integer page, Integer size) {
        IPage<Review> result = page(new Page<>(page, size), new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .orderByDesc(Review::getCreatedAt));
        enrichReviewUsers(result.getRecords());
        return result;
    }

    @Override
    public IPage<Review> getMyReviews(Long userId, Integer page, Integer size) {
        IPage<Review> result = page(new Page<>(page, size), new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreatedAt));
        enrichReviewUsers(result.getRecords());
        return result;
    }

    private void enrichReviewUsers(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }

        Set<Long> userIds = reviews.stream()
                .map(Review::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        for (Review review : reviews) {
            User user = userMap.get(review.getUserId());
            if (user == null) {
                continue;
            }
            review.setUserNickname(user.getNickname());
            review.setUserAvatar(user.getAvatar());
        }
    }
}
