package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerService;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сценарии работы с заказами. Только оркестрирует шаги - валидация,
 * клиент, расчёт цены и уведомление вынесены в отдельные классы.
 */
@Service
public class OrderService {

    private final OrderValidator validator;
    private final CustomerService customerService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PricingCalculator pricingCalculator;
    private final OrderNotifier notifier;

    public OrderService(
            OrderValidator validator,
            CustomerService customerService,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PricingCalculator pricingCalculator,
            OrderNotifier notifier) {
        this.validator = validator;
        this.customerService = customerService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.pricingCalculator = pricingCalculator;
        this.notifier = notifier;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);
        Customer customer = customerService.findOrCreate(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = new Order(customer, Order.STATUS_NEW);
        for (CreateOrderRequest.Item item : request.items()) {
            order.addItem(findProduct(item.productId()), item.quantityOrDefault());
        }
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                toLineItems(order), request.customerTypeOrDefault(), request.couponCode());
        Order saved = orderRepository.save(order);
        notifier.orderPlaced(saved, total);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> findAllWithItems() {
        return orderRepository.findAllWithItems();
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("product " + productId + " not found"));
    }

    private static List<PricingCalculator.LineItem> toLineItems(Order order) {
        return order.getItems().stream()
                .map(item -> new PricingCalculator.LineItem(item.getProduct().getPrice(), item.getQuantity()))
                .toList();
    }
}
