package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.entity.Product;
import com.heartlink.entity.Review;
import com.heartlink.service.AiService;
import com.heartlink.service.HealthProfileService;
import com.heartlink.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private HealthProfileService healthProfileService;
    @Mock
    private AiService aiService;
    @Mock
    private ReviewService reviewService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = spy(new ProductServiceImpl(
                healthProfileService,
                aiService,
                reviewService
        ));
    }

    @Test
    void getProductDetail_shouldExposeReviewPreviewInsteadOfSummary() {
        Product product = new Product();
        product.setId(5L);
        product.setReviewsSummary("旧的评价总结");

        Review review = new Review();
        review.setId(11L);
        review.setProductId(5L);
        review.setUserId(22L);
        review.setContent("很适合老人使用");

        IPage<Review> reviewPage = new Page<>(1, 6);
        reviewPage.setRecords(List.of(review));

        doReturn(product).when(productService).getById(5L);
        when(reviewService.getProductReviews(5L, 1, 6)).thenReturn(reviewPage);

        Product detail = productService.getProductDetail(5L);

        assertNull(detail.getReviewsSummary());
        assertEquals(1, detail.getReviewPreview().size());
        assertEquals("很适合老人使用", detail.getReviewPreview().get(0).getContent());
    }
}
