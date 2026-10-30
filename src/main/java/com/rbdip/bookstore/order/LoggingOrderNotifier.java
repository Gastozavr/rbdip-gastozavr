package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Реальный почтовый транспорт в учебном проекте не настроен - письмо
 * клиенту эмулируется записью в лог.
 */
@Component
public class LoggingOrderNotifier implements OrderNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingOrderNotifier.class);

    @Override
    public void orderPlaced(Order order, BigDecimal total) {
        String customerName = order.getCustomerFullName();
        Long orderId = order.getId();
        LOG.info("[email] Dear {}, your order #{} for {} has been placed.", customerName, orderId, total);
    }
}
