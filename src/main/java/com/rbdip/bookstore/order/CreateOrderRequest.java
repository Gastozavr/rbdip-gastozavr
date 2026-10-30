package com.rbdip.bookstore.order;

import java.util.List;

public record CreateOrderRequest(
        String customerFullName,
        String customerAddress,
        String customerPhone,
        String customerType,
        String couponCode,
        List<Item> items) {

    public static final String DEFAULT_CUSTOMER_TYPE = "regular";

    public String customerTypeOrDefault() {
        return customerType == null ? DEFAULT_CUSTOMER_TYPE : customerType;
    }

    public record Item(Long productId, Integer quantity) {

        public int quantityOrDefault() {
            return quantity == null ? 1 : quantity;
        }
    }
}
