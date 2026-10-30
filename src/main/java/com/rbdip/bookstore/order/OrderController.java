package com.rbdip.bookstore.order;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return Map.of("id", order.getId(), "status", order.getStatus());
    }

    @GetMapping("/orders")
    public List<OrderSummary> listOrders() {
        return orderService.findAllWithItems().stream()
                .map(OrderSummary::from)
                .toList();
    }

    public record OrderSummary(Long id, String customerFullName, String status, List<ItemSummary> items) {

        static OrderSummary from(Order order) {
            List<ItemSummary> items = order.getItems().stream()
                    .map(item -> new ItemSummary(item.getProduct().getName(), item.getQuantity()))
                    .toList();
            return new OrderSummary(order.getId(), order.getCustomerFullName(), order.getStatus(), items);
        }
    }

    public record ItemSummary(String productName, Integer quantity) {
    }
}
