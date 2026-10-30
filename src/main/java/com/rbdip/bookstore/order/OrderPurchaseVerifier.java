package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.PersonName;
import com.rbdip.bookstore.purchase.PurchaseVerifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Реализация контракта {@link PurchaseVerifier} поверх данных заказов. */
@Component
class OrderPurchaseVerifier implements PurchaseVerifier {

    private final OrderItemRepository orderItemRepository;

    OrderPurchaseVerifier(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchased(String customerFullName, Long productId) {
        if (customerFullName == null || customerFullName.isBlank() || productId == null) {
            return false;
        }
        PersonName name = PersonName.parse(customerFullName);
        return orderItemRepository.existsPurchase(name.firstName(), name.lastName(), productId);
    }
}
