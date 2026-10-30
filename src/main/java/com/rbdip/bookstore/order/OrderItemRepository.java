package com.rbdip.bookstore.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select count(i) > 0 from OrderItem i"
            + " where i.product.id = :productId"
            + " and i.order.customer.firstName = :firstName"
            + " and i.order.customer.lastName = :lastName")
    boolean existsPurchase(
            @Param("firstName") String firstName, @Param("lastName") String lastName, @Param("productId") Long productId);
}
