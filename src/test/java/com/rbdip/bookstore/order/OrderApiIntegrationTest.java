package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Поведение API заказов и отзывов на нормализованной схеме (ЛР2-ЛР4). */
class OrderApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void repeatedOrdersOfSameCustomerReuseCustomerRow() {
        Long productId = createProduct("Clean Code", "35.00");

        placeOrder("Ivan Petrov", "+79990000000", productId, 1);
        placeOrder("Ivan Petrov", "+79990000000", productId, 2);
        placeOrder("Ivan Petrov", null, productId, 1);

        assertThat(count("customers")).isEqualTo(2);
        assertThat(count("orders")).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT first_name || '|' || last_name FROM customers", String.class))
                .containsOnly("Ivan|Petrov");
    }

    @Test
    void orderItemsReferenceProducts() {
        Long productId = createProduct("Refactoring", "40.00");

        placeOrder("Anna Sidorova", null, productId, 3);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT product_id, quantity FROM order_items");
        assertThat(row).containsEntry("product_id", productId).containsEntry("quantity", 3);
    }

    @Test
    void listsOrdersWithCustomerNameAndProducts() {
        Long first = createProduct("Refactoring", "40.00");
        Long second = createProduct("DDD", "50.00");
        placeOrder("Madonna", null, first, 1);
        placeOrder("Anna Sidorova", null, second, 2);

        ResponseEntity<List> response = restTemplate.getForEntity("/orders", List.class);

        assertThat(response.getBody()).hasSize(2);
        Map<?, ?> order = (Map<?, ?>) response.getBody().get(0);
        assertThat(order.get("customerFullName")).isEqualTo("Madonna");
        assertThat(order.get("status")).isEqualTo("new");
        assertThat((List<Object>) order.get("items"))
                .containsExactly(Map.of("productName", "Refactoring", "quantity", 1));
    }

    @Test
    void rejectsUnknownProduct() {
        ResponseEntity<Map> response = placeOrder("Ivan Petrov", null, 999L, 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "product 999 not found");
        assertThat(count("customers")).isZero();
    }

    @Test
    void reviewFromBuyerIsVerifiedPurchase() {
        Long productId = createProduct("Clean Code", "35.00");
        Long otherProductId = createProduct("Refactoring", "40.00");
        placeOrder("Ivan Petrov", null, productId, 1);

        assertThat(addReview(productId, "Ivan Petrov")).containsEntry("verifiedPurchase", true);
        assertThat(addReview(otherProductId, "Ivan Petrov")).containsEntry("verifiedPurchase", false);
        assertThat(addReview(productId, "Anna Sidorova")).containsEntry("verifiedPurchase", false);
        assertThat(addReview(productId, null)).containsEntry("verifiedPurchase", false);

        ResponseEntity<List> reviews = restTemplate.getForEntity("/products/" + productId + "/reviews", List.class);
        assertThat(reviews.getBody()).hasSize(3);
    }

    private Long createProduct(String name, String price) {
        Map<?, ?> body = restTemplate.postForEntity(
                "/products", Map.of("name", name, "price", new BigDecimal(price)), Map.class).getBody();
        return Long.valueOf(body.get("id").toString());
    }

    private ResponseEntity<Map> placeOrder(String customer, String phone, Long productId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest(
                customer, "Moscow, Lenina 1", phone, null, null,
                List.of(new CreateOrderRequest.Item(productId, quantity)));
        return restTemplate.postForEntity("/orders", request, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> addReview(Long productId, String author) {
        Map<String, Object> body = new HashMap<>(Map.of("rating", 5));
        body.put("authorName", author);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/products/" + productId + "/reviews", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map<String, Object>) response.getBody();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
