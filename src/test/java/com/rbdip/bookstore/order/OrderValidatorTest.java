package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    private static CreateOrderRequest request(String name, String address, List<CreateOrderRequest.Item> items) {
        return new CreateOrderRequest(name, address, null, null, null, items);
    }

    private static List<CreateOrderRequest.Item> oneItem(Integer quantity) {
        return List.of(new CreateOrderRequest.Item(1L, quantity));
    }

    @Test
    void acceptsValidRequest() {
        assertThatCode(() -> validator.validate(request("Ivan Petrov", "Moscow", oneItem(2))))
                .doesNotThrowAnyException();
    }

    @Test
    void missingQuantityDefaultsToOne() {
        assertThatCode(() -> validator.validate(request("Ivan Petrov", "Moscow", oneItem(null))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void requiresCustomerName(String name) {
        assertThatThrownBy(() -> validator.validate(request(name, "Moscow", oneItem(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customerFullName is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void requiresAddress(String address) {
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", address, oneItem(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customerAddress is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void requiresItems(List<CreateOrderRequest.Item> items) {
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", items)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("order must contain at least one item");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantity(int quantity) {
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", oneItem(quantity))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive");
    }

    @Test
    void requiresProductId() {
        List<CreateOrderRequest.Item> items = List.of(new CreateOrderRequest.Item(null, 1));
        assertThatThrownBy(() -> validator.validate(request("Ivan Petrov", "Moscow", items)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("productId is required");
    }
}
