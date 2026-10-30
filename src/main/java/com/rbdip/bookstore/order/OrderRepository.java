package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Заказы вместе с клиентом, позициями и товарами - одним SQL-запросом (без N+1). */
    @Query("select distinct o from Order o"
            + " join fetch o.customer"
            + " left join fetch o.items i"
            + " left join fetch i.product"
            + " order by o.id")
    List<Order> findAllWithItems();
}
