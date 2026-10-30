package com.rbdip.bookstore.order;

import org.springframework.stereotype.Component;

/** Проверка входного запроса на создание заказа (без обращений к БД). */
@Component
public class OrderValidator {

    public void validate(CreateOrderRequest request) {
        requireText(request.customerFullName(), "customerFullName is required");
        requireText(request.customerAddress(), "customerAddress is required");
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
        request.items().forEach(OrderValidator::validateItem);
    }

    private static void validateItem(CreateOrderRequest.Item item) {
        if (item.productId() == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (item.quantityOrDefault() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
