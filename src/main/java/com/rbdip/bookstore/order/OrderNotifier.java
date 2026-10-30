package com.rbdip.bookstore.order;

import java.math.BigDecimal;

/** Уведомление клиента о размещённом заказе. */
public interface OrderNotifier {

    void orderPlaced(Order order, BigDecimal total);
}
