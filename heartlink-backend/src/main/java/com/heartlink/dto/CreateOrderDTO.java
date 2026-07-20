package com.heartlink.dto;

import lombok.Data;

/**
 * Create-order request.
 * Supports either shelf-based ordering or direct product ordering.
 */
@Data
public class CreateOrderDTO {

    /** Smart shelf item id. */
    private Long shelfId;

    /** Direct product id. */
    private Long productId;

    /** Target parent id for direct product orders. */
    private Long parentId;

    /** Quantity. */
    private Integer quantity = 1;

    /** Receiver name. */
    private String receiverName;

    /** Receiver phone. */
    private String receiverPhone;

    /** Receiver address. */
    private String receiverAddress;

    /** Whether to generate a greeting card. */
    private Boolean generateGreeting = true;
}
