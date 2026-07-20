package com.heartlink.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.dto.PayOrderDTO;
import com.heartlink.entity.PaymentRequest;

import java.util.List;

public interface PaymentRequestService extends IService<PaymentRequest> {

    PaymentRequest createRequest(Long orderId, Long parentId, String message);

    PaymentRequest createRequestByProduct(Long productId, Long parentId, String message);

    List<PaymentRequest> getPendingRequests(Long childId);

    IPage<PaymentRequest> getChildRequests(Long childId, Integer page, Integer size, String status);

    IPage<PaymentRequest> getParentRequests(Long parentId, Integer page, Integer size, String status);

    PaymentRequest payRequest(Long requestId, Long childId);

    PaymentRequest payRequest(Long requestId, Long childId, PayOrderDTO dto);

    PaymentRequest rejectRequest(Long requestId, Long childId);

    int getPendingCount(Long childId);
}
