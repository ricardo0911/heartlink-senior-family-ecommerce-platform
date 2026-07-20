package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.entity.Review;
import com.heartlink.entity.User;
import com.heartlink.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private UserService userService;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = spy(new ReviewServiceImpl(userService));
    }

    @Test
    void getProductReviews_shouldAttachUserProfile() {
        Review review = new Review();
        review.setId(1L);
        review.setProductId(8L);
        review.setUserId(10L);
        review.setContent("很实用");
        review.setRating(5);

        IPage<Review> pageResult = new Page<Review>(1, 10).setRecords(List.of(review));
        doReturn(pageResult).when(reviewService).page(any(Page.class), any(LambdaQueryWrapper.class));

        User user = new User();
        user.setId(10L);
        user.setNickname("小雨");
        user.setAvatar("/upload/avatar.png");
        when(userService.listByIds(any())).thenReturn(List.of(user));

        IPage<Review> result = reviewService.getProductReviews(8L, 1, 10);

        assertEquals("小雨", result.getRecords().get(0).getUserNickname());
        assertEquals("/upload/avatar.png", result.getRecords().get(0).getUserAvatar());
    }
}
