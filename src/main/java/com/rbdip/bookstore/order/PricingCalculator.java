package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * Расчёт итоговой стоимости заказа. Скидки применяются строго в таком
 * порядке: оптовая скидка на позицию, скидка по типу клиента, купон,
 * ограничение снизу нулём, скидка на крупный заказ, округление до копеек.
 */
@Component
public class PricingCalculator {

    /** Позиция с количеством строго больше порога получает оптовую скидку. */
    static final int BULK_QUANTITY_THRESHOLD = 10;
    static final BigDecimal BULK_DISCOUNT_FACTOR = new BigDecimal("0.95");

    /** Заказ на сумму строго больше порога получает дополнительную скидку. */
    static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    static final BigDecimal LARGE_ORDER_DISCOUNT_FACTOR = new BigDecimal("0.98");

    private static final int MONEY_SCALE = 2;

    private static final Map<String, BigDecimal> CUSTOMER_DISCOUNT_FACTORS = Map.of(
            "vip", new BigDecimal("0.90"),
            "wholesale", new BigDecimal("0.85"));

    private static final Map<String, UnaryOperator<BigDecimal>> COUPONS = Map.of(
            "SAVE10", total -> total.subtract(BigDecimal.TEN),
            "SAVE20PERCENT", total -> total.multiply(new BigDecimal("0.80")));

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = items.stream()
                .map(PricingCalculator::linePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        total = applyCustomerDiscount(total, customerType);
        total = applyCoupon(total, couponCode);
        total = total.max(BigDecimal.ZERO);
        total = applyLargeOrderDiscount(total);
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal linePrice(LineItem item) {
        BigDecimal linePrice = item.price().multiply(BigDecimal.valueOf(item.quantity()));
        return item.quantity() > BULK_QUANTITY_THRESHOLD ? linePrice.multiply(BULK_DISCOUNT_FACTOR) : linePrice;
    }

    private static BigDecimal applyCustomerDiscount(BigDecimal total, String customerType) {
        return Optional.ofNullable(customerType)
                .map(CUSTOMER_DISCOUNT_FACTORS::get)
                .map(total::multiply)
                .orElse(total);
    }

    private static BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        return Optional.ofNullable(couponCode)
                .map(COUPONS::get)
                .map(coupon -> coupon.apply(total))
                .orElse(total);
    }

    private static BigDecimal applyLargeOrderDiscount(BigDecimal total) {
        return total.compareTo(LARGE_ORDER_THRESHOLD) > 0 ? total.multiply(LARGE_ORDER_DISCOUNT_FACTOR) : total;
    }
}
